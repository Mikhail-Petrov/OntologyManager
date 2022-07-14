package om;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

public class Main {

	private static boolean exit = false;
	private static OntModel memModel;
	private static Model memModel2;
	private static Repository db = new SailRepository(new MemoryStore());
	private static final String subClass = "rdfs:subClassOf", isDefinedBy = "rdfs:isDefinedBy", seeAlso = "rdfs:seeAlso", disjointWith = "owl:disjointWith";
	
	private static List<String> subscribes = new ArrayList<>();
	private static List<DataOutputStream> outs = new ArrayList<>();
	
	public static void main(String[] args) {
	    ArrayList<Closeable> toClose = new ArrayList<>();
	    ArrayList<Thread> threads = new ArrayList<>();
		try {
		    ServerSocket serverSock = new ServerSocket(6066);
			//Scanner in = new Scanner(System.in);
			while (!exit) {
			    Socket Sock = serverSock.accept();
			    DataOutputStream out = new DataOutputStream(Sock.getOutputStream());
			    DataInputStream in = new DataInputStream(Sock.getInputStream());
			    toClose.add(Sock);
			    toClose.add(out);
			    toClose.add(in);
			    
				Runnable task = () -> {
					while (!exit) {
						String res;
						try {
							try (RepositoryConnection conn = db.getConnection()) {
								res = processComand(in.readUTF(), out, conn);
							}
							if (res.isEmpty()) {
								Sock.close();
								in.close();
								out.close();
								break;
							}
							if (res.length() > 65535)
								res = "too many results";
							out.writeUTF(res);
						} catch (IOException e) {
							e.printStackTrace();
						}
						finally {
						  // before our program exits, make sure the database is properly shut down.
							if (db != null)
								db.shutDown();
						}
					}
				};
				Thread thread = new Thread(task);
				thread.start();
				threads.add(thread);
			}
		    serverSock.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			threads.forEach(x -> x.interrupt());
			toClose.forEach(x -> {
				try {
					x.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			
		}
		
	}

	private static String processComand(String json, DataOutputStream out, RepositoryConnection conn) {
		JSONObject object = new JSONObject(json);
		String command = object.getString("command");
		System.out.println(command);
		switch (command) {
		case "e":
			return "";
		case "ee":
			exit = true;
			try {
				Socket sock = new Socket("localhost", 6066);
				sock.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return "";
		case "generate":
			int n = object.getInt("size");
			if (n > 0)
				generate(n);
			return String.format("%d axioms has been generated.", n);
		case "write":
			if (memModel == null)
				return "model is empty";
			try {
				memModel.write(new FileOutputStream("../ontology.xml"));
			} catch (FileNotFoundException e1) {
			}
			return "model has been wroten";
		case "read":
			String name = object.has("path") ? object.getString("path") : "../ontology.xml";
			memModel = ModelFactory.createOntologyModel();
			try {
				memModel.read(new FileInputStream(name), null);
				memModel2 = Rio.parse(new FileInputStream(name), RDFFormat.RDFXML);
				conn.add(memModel2);
			} catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
				e.printStackTrace();
			}
			//memModel.write(System.out);
			return "model has been read";
		case "subscribe":
			//subscribes.add(tripleToFilter(object.getJSONObject("triple")));
			subscribes.add(object.getString("query"));
			outs.add(out);
			return "you has been subscribed";
		case "unsubscribe":
			//subscribes.remove(tripleToFilter(object.getJSONObject("triple")));
			subscribes.remove(object.getString("query"));
			return "you has been unsubscribed";
		case "remove":
			JSONArray rtriples = object.getJSONArray("triples");
			OntClass rsubject = null;
			Property rpredicate = null;
			OntClass robject = null;
			for (int i = 0; i < rtriples.length(); i++) {
				JSONObject rtriple = rtriples.getJSONObject(i);
				String rs = rtriple.get("subject").toString(), rp = rtriple.get("predicate").toString(), ro = rtriple.get("object").toString();
				rs = rs == null ? "" : rs;
				rp = rp == null ? "" : rp;
				ro = ro == null ? "" : ro;
				// get subject and object
				rsubject = memModel.getOntClass(rs);
				robject = memModel.getOntClass(ro);
				if (rsubject == null || robject == null)
					continue;
				// if predicate is one of the basic properties, than remove it from the subject
				if (rp.equals(subClass))
					robject.removeSubClass(robject);
				else if (rp.equals(seeAlso))
					rsubject.removeSeeAlso(robject);
				else if (rp.equals(disjointWith))
					rsubject.removeDisjointWith(robject);
				else if (rp.equals(isDefinedBy))
					rsubject.removeDefinedBy(robject);
				else {
					// otherwise get property and remove it from the subject
					rpredicate = memModel.getProperty(rp);
					rsubject.removeProperty(rpredicate, robject);
				}
			}
			return "triples has been removed";
		case "insert":
			JSONArray triples = object.getJSONArray("triples");
			OntClass subject = null;
			Property predicate = null;
			OntClass tr_object = null;
			for (int i = 0; i < triples.length(); i++) {
				JSONObject triple = triples.getJSONObject(i);
				String s = triple.get("subject").toString(), p = triple.get("predicate").toString(), o = triple.get("object").toString();
				s = s == null ? "" : s;
				p = p == null ? "" : p;
				o = o == null ? "" : o;
				System.out.println(String.format("s: %s\np: %s\no: %s\n", s, p, o));
				// get or create subject and object
				subject = memModel.getOntClass(s);
				if (subject == null && !s.isEmpty())
					subject = memModel.createClass(s);
				tr_object = memModel.getOntClass(o);
				if (tr_object == null && !o.isEmpty())
					tr_object = memModel.createClass(o);
				if (subject == null || object == null)
					continue;
				// if predicate is one of the basic properties, than add it to the subject
				if (p.equals(subClass))
					tr_object.addSubClass(subject);
				else if (p.equals(seeAlso))
					subject.addSeeAlso(tr_object);
				else if (p.equals(disjointWith))
					subject.addDisjointWith(tr_object);
				else if (p.equals(isDefinedBy))
					subject.addIsDefinedBy(tr_object);
				else {
					// otherwise get or create property and add it to the subject
					predicate = memModel.getProperty(p);
					if (predicate == null && !p.isEmpty())
						predicate = memModel.createProperty(p);
					subject.addProperty(predicate, tr_object);
				}
				// TODO: save new triples for checking subscribes
			}
			// TODO: checking subscribes
			return "triples has been added";
		case "addIndividual":
			String addIndClassName = object.getString("class"), addIndName = object.getString("name");
			// get or create class
			OntClass addIndOntClass = memModel.getOntClass(addIndClassName);
			if (addIndOntClass == null && !addIndClassName.isEmpty())
				addIndOntClass = memModel.createClass(addIndClassName);
			if (addIndOntClass == null)
				return "failed to get class " + addIndClassName;
			// add individual
			addIndOntClass.createIndividual(addIndName);
			return "individual " + addIndName + " has been added to class " + addIndClassName;
		case "removeIndividual":
			String remIndClassName = object.getString("class"), remIndName = object.getString("name");
			// get or create class
			OntClass remIndOntClass = memModel.getOntClass(remIndClassName);
			if (remIndOntClass == null && !remIndClassName.isEmpty())
				remIndOntClass = memModel.createClass(remIndClassName);
			if (remIndOntClass == null)
				return "failed to get class " + remIndClassName;
			// get and drop individual
			Individual indToDrop = memModel.getIndividual(remIndName);
			if (indToDrop != null && indToDrop.hasOntClass(remIndOntClass)) {
				remIndOntClass.dropIndividual(indToDrop);
				return "individual " + remIndName + " has been removed from class " + remIndClassName;
			} else
				return "failed to remove individual " + remIndName + " from class " + remIndClassName;
		case "query":
			if (memModel == null)
				return "model is empty";
			String res = "";
			String queryString = object.getString("query");
			// execute query
			Query query = QueryFactory.create(queryString);
			long startTime = System.currentTimeMillis(), finishTime = 0;
			try (QueryExecution qexec = QueryExecutionFactory.create(query, memModel)) {
				ResultSet results = qexec.execSelect();
				finishTime = System.currentTimeMillis();
				// save results in json
				JSONArray resTriples = new JSONArray();
				List<String> resultVars = results.getResultVars();
				results.forEachRemaining(x -> {
					JSONObject resTriple = new JSONObject();
					// each variable in a separate field of the object
					resultVars.forEach(y -> resTriple.put(y, x.get(y).toString()));
					resTriples.put(resTriple);
				});
			    JSONObject resReturn = new JSONObject();
			    resReturn.put("type", "array");
			    resReturn.put("data", resTriples);
			    res = resReturn.toString();
				if (finishTime > 0)
					System.out.println(String.format("%d\t%d", resTriples.length(), finishTime - startTime));
			}
			try (QueryExecution qexec = QueryExecutionFactory.create(query, memModel)) {
				ResultSet results = qexec.execSelect();
			   // ResultSetFormatter.out(System.out, results, query);
			}
			
			TupleQuery query2 = conn.prepareTupleQuery(queryString);
			startTime = System.currentTimeMillis(); finishTime = 0;
			try (TupleQueryResult result = query2.evaluate()) {
				List<String> bindingNames = result.getBindingNames();
				finishTime = System.currentTimeMillis();
				if (!bindingNames.isEmpty()) {
					int resAmount = 0;
					for (BindingSet solution : result) {
						System.out.println(String.format("?%s = %s", bindingNames.get(0), solution.getValue(bindingNames.get(0))));
						resAmount++;
					}
					finishTime = System.currentTimeMillis();
					if (finishTime > 0)
						System.out.println(String.format("%d\t%d", resAmount, finishTime - startTime));
				} else
					System.out.println("no results");
			}
			return res;
			//return "query result:\n" + res;
		default:
			return "Wrong command: " + command;
			//System.out.println(command.toUpperCase());
		}
	}
	
	private static void generate(int n) {
		OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
		Random r = new Random();
		List<OntClass> classes = new ArrayList<>();
		int stmts = 0;
		while (stmts < n) {
			if (stmts - (stmts/10000)*10000 < 5)
				System.out.println(String.format("%d\t%d", System.currentTimeMillis(), stmts));
			String className = "http://example/"+"class" + stmts;
			OntClass res = model.createClass(className);
			int individs = r.nextInt(3) + 1;
			for (int i = 0; i < individs; i++)
				res.createIndividual(className+"/ind" + i);
			stmts += individs;
			List<Integer> indexes = new ArrayList<>();
			if (classes.size() > 0) {
				int ind = r.nextInt(classes.size());
				classes.get(ind).addSubClass(res);
				indexes.add(ind);
			}
			stmts++;
			individs = r.nextInt(3) + 1;
			for (int i = 0; i < individs && classes.size() > indexes.size(); i++){
				int ind = r.nextInt(classes.size());
				while (indexes.contains(ind))
					ind = r.nextInt(classes.size());
				res.addSeeAlso(classes.get(ind));
				indexes.add(ind);
			}
			/*stmts += individs;
			individs = r.nextInt(3) + 1;
			for (int i = 0; i < individs && classes.size() > indexes.size(); i++){
				int ind = r.nextInt(classes.size());
				while (indexes.contains(ind))
					ind = r.nextInt(classes.size());
				res.addDisjointWith(classes.get(ind));
				indexes.add(ind);
			}*/
			stmts += individs;
			individs = r.nextInt(3) + 1;
			for (int i = 0; i < individs && classes.size() > indexes.size(); i++){
				int ind = r.nextInt(classes.size());
				while (indexes.contains(ind))
					ind = r.nextInt(classes.size());
				res.addIsDefinedBy(classes.get(ind));
				indexes.add(ind);
			}
			stmts += individs;
			classes.add(res);
		}
		try {
			model.write(new FileOutputStream("../ontology.xml"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static String tripleToFilter(JSONObject triple) {
		String res = "";
		String s = triple.get("subject").toString(), p = triple.get("predicate").toString(), o = triple.get("object").toString();
		s = s == null ? "" : s;
		p = p == null ? "" : p;
		o = o == null ? "" : o;
		// TODO: convert triple to SPARQL query
		
		return res;
	}
}
