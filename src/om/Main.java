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
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main {

	private static String delimiter = "||";
	private static boolean exit = false;
	private static OntModel memModel;
	
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
							res = processComand(in.readUTF());
							if (res.isEmpty()) {
								Sock.close();
								in.close();
								out.close();
								break;
							}
							out.writeUTF(res);
						} catch (IOException e) {
							e.printStackTrace();
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

	private static String processComand(String json) {
		JSONObject object = new JSONObject(json);
		String command = object.getString("command");
		System.out.println(command);
		if (command.equals("e"))
			return "";
		if (command.equals("ee")) {
			exit = true;
			try {
				Socket sock = new Socket("localhost", 6066);
				sock.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return "";
		}
		if (command.startsWith("generate")) {
			int n = object.getInt("size");
			if (n > 0)
				generate(n);
			return String.format("%d axioms has been generated.", n);
		} else if (command.startsWith("read")) {
			String name = object.has("path") ? object.getString("path") : "../ontology.txt";
			memModel = ModelFactory.createOntologyModel();
			try {
				memModel.read(new FileInputStream(name), null);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			memModel.write(System.out);
			return "test";
		} else if (command.startsWith("subscribe")) {
			String triple = command.substring("subscribe".length()+1);
			String[] tri = triple.split(delimiter);
			// TODO: subscribe
			
			return "you has been subscribed";
		} else if (command.startsWith("unsubscribe")) {
			String triple = command.substring("unsubscribe".length()+1);
			String[] tri = triple.split(delimiter);
			// TODO: unsubscribe
			
			return "you has been unsubscribed";
		} else if (command.startsWith("insert")) {
			JSONArray triples = object.getJSONArray("triples");
			for (int i = 0; i < triples.length(); i++) {
				JSONObject triple = triples.getJSONObject(i);
				System.out.println(String.format("s: %s\np: %s\no: %s\n", triple.get("subject"), triple.get("predicate"), triple.get("object")));
			}
			// TODO: insert triple
			
			return "triples has been added";
		} else if (command.startsWith("query")) {
			String res = "";
			String queryString = object.getString("query");
			// TODO: execute query
			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query, memModel)) {
				ResultSet results = qexec.execSelect();
			    ResultSetFormatter.out(System.out, results, query);
			}
			return "";
			//return "query result:\n" + res;
		} else
			return "Wrong command: " + command;
			//System.out.println(command.toUpperCase());
		
	}
	
	private static void generate(int n) {
		OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
		Random r = new Random();
		List<OntClass> classes = new ArrayList<>();
		int stmts = 0;
		while (stmts < n) {
			String className = "http://example/"+"class" + stmts;
			OntClass res = model.createClass(className);
			int individs = r.nextInt(3) + 1;
			for (int i = 0; i < individs; i++)
				res.createIndividual(className+"/ind" + i);
			stmts += individs;
			if (classes.size() > 0) {
				int ind = r.nextInt(classes.size());
				classes.get(ind).addSubClass(res);
			}
			stmts++;
			/*int rels = r.nextInt(5) + 1;
			List<Integer> indexes = new ArrayList<>();
			for (int i = 0; i < rels && resources.size() > indexes.size(); i++) {
				int ind = r.nextInt(resources.size());
				while (indexes.contains(ind))
					ind = r.nextInt(resources.size());
				
				indexes.add(ind);
				res.addProperty(VCARD.N, resources.get(ind));
			}
			stmts += rels;*/
			classes.add(res);
		}
		try {
			model.write(new FileOutputStream("../ontology.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
