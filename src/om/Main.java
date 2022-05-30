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

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

public class Main {

	private static String delimiter = "||";
	private static boolean exit = false;
	
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

	private static String processComand(String command) {
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
			int n = 0;
			String[] coms = command.split(" ");
			try {
				if (coms.length > 1)
					n = Integer.parseInt(coms[1]);
			} catch (NumberFormatException e) {}
			if (n > 0)
				generate(n);
			return String.format("%d axioms has been generated.", n);
		} else if (command.startsWith("read")) {
			String name = "../ontology.txt";
			String[] coms = command.split(" ");
			if (coms.length > 1)
				name = coms[1];
			Model model = ModelFactory.createDefaultModel();
			try {
				model.read(new FileInputStream(name), null);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			model.write(System.out);
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
			String triple = command.substring("insert".length()+1);
			String[] tri = triple.split(delimiter);
			// TODO: insert triple
			
			return "triples has been added";
		} else if (command.startsWith("query")) {
			String res = "";
			String query = command.substring("query".length()+1);
			// TODO: execute query
			
			return "query result:\n" + res;
		} else
			return "Wrong command: " + command;
			//System.out.println(command.toUpperCase());
		
	}
	
	private static void generate(int n) {
		Model model = ModelFactory.createDefaultModel();
		Random r = new Random();
		List<Resource> resources = new ArrayList<>();
		int stmts = 0;
		while (stmts < n) {
			Resource res = model.createResource();
			int props = r.nextInt(3) + 1;
			for (int i = 0; i < props; i++)
				res.addProperty(VCARD.LABEL, "prop" + (stmts + i));
			stmts += props;
			int rels = r.nextInt(5) + 1;
			List<Integer> indexes = new ArrayList<>();
			for (int i = 0; i < rels && resources.size() > indexes.size(); i++) {
				int ind = r.nextInt(resources.size());
				while (indexes.contains(ind))
					ind = r.nextInt(resources.size());
				
				indexes.add(ind);
				res.addProperty(VCARD.N, resources.get(ind));
			}
			stmts += rels;
			resources.add(res);
		}
		try {
			model.write(new FileOutputStream("../ontology.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
