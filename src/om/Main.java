package om;
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
import java.util.Scanner;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

public class Main {

	public static void main(String[] args) {
		try {
		    ServerSocket serverSock = new ServerSocket(6066);
		    Socket Sock=serverSock.accept();
		    DataOutputStream out =new DataOutputStream(Sock.getOutputStream());
		    DataInputStream in = new DataInputStream(Sock.getInputStream());
			//Scanner in = new Scanner(System.in);
			for (;;) {
				String command = in.readUTF();
				System.out.println(command);
				if (command.equals("e"))
					break;
				if (command.startsWith("generate")) {
					int n = 0;
					String[] coms = command.split(" ");
					if (coms.length > 1)
						n = Integer.parseInt(coms[1]);
					if (n > 0)
						generate(n);
				} else
				if (command.startsWith("read")) {
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
				} else
					System.out.println(command.toUpperCase());
			}
		    Sock.close();   
		    serverSock.close();
			in.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
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
