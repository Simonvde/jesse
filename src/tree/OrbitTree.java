package tree;

/*
 * #%L
 * Jesse
 * %%
 * Copyright (C) 2017 Intec/UGent - Ine Melckenbeeck
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import codegenerating.TreeInterpreter;
import orbits.Edge;
import orbits.OrbitIdentification;
import orbits.OrbitRepresentative;
import progress.TaskMonitor;

/**
 * A tree in which the construction of orbit representatives is shown, starting
 * from a two-node orbit representative.
 * 
 * @author Ine Melckenbeeck
 *
 */
public class OrbitTree {

	private AddNodeNode root;
	private int order;
	private TreeInterpreter interpreter;
	private Set<OrbitRepresentative> leaves;
	
	private TaskMonitor taskMonitor;

	/**
	 * Builds a new OrbitTree containing OrbitRepresentatives up to the given
	 * order.
	 * 
	 * @param order
	 *            The maximal order of the OrbitRepresentatives in the tree.
	 */
	public OrbitTree(int order) {
		this.order = order;
		buildTreeBFS();
	}

	/**
	 * Initialize an empty tree. Needs {@link setOrder()} and {@link buildTreeBFS()} to be called after.
	 */
	public OrbitTree() {
	}
	
	public void buildTreeBFS() {
		if (taskMonitor != null) {
			taskMonitor.setProgress(0);
			taskMonitor.setStatusMessage("Building orbit tree");
		}
		int counter = 0;
		OrbitRepresentative o = new OrbitRepresentative();
		root = new AddNodeNode(o,this);
		OrbitRepresentative o2 = new OrbitRepresentative(o);
		boolean[] z = { true };
		o2.addNode(z);
//		o2.calculateSymmetry();

		AddNodeNode node = new AddNodeNode(o2, root);
		root.addChild(0, node);
		Deque<AddNodeNode> nodes1 = new LinkedList<AddNodeNode>();
		nodes1.add(node);
		for (int k = 2; k < order; k++) {
			Deque<AddEdgeNode> nextEdges = new LinkedList<AddEdgeNode>();
			Map<OrbitRepresentative,AddNodeNode> used = new HashMap<>();
//			System.out.println(nodes1);
			for (AddNodeNode node1 : nodes1) {
				if (taskMonitor !=null && taskMonitor.isCancelled()) {
					return;
				}
				/* Add a first new layer of AddEdgeNode. */
				OrbitRepresentative or = node1.getOrbitRepresentative();
				if (used.containsKey(or)) {
					/* Make sure each branch ends in an AddNodeNode. */
//						AddNodeNode ann = used.get(or);
//						AddNode oldparent = (AddNode)ann.getParent();
//						AddNode newparent = (AddNode)node1.getParent(); 
//					if(newparent.getOrbitRepresentative().compareTo(oldparent.getOrbitRepresentative())>0){
//						ann.remove();
//						ann.prune();
//						used.put(or,node1);
//					}else{
					node1.remove();
					node1.getParent().prune();
//					}
				} else {
					counter++;
					if(taskMonitor!=null) {
						taskMonitor.setProgress((double)(counter)/OrbitIdentification.getNOrbitsTotal(order));
					}
					
					used.put(or,node1);
					/*
					 * Only connect to one node of each suborbit, the other
					 * situations will get the same orbit representative anyway.
					 */
					OrbitRepresentative copy = new OrbitRepresentative(or);
					copy.addNode(0);
					AddEdgeNode child = new AddEdgeNode(copy, node1, 1);
					nextEdges.add(child);
					node1.addChild(0, child);
					
					for (int i = 1; i < or.getOrbits().size(); i++) {
						int index = or.getOrbits().get(i).first();
						copy = new OrbitRepresentative(or);
						copy.addNode(index);
						AddEdgeNode parent = new AddEdgeNode(copy,node1,0);
						node1.addChild(index, parent);
						for(int j = 1;j<index;j++){
							AddEdgeNode previousParent = parent;
							parent = new AddEdgeNode(copy,previousParent,j);
							previousParent.addChild(parent, false);
						}
						copy = new OrbitRepresentative(copy);
						if(index<or.order()-1){
						 child = new AddEdgeNode(copy, parent, index+1);}
						else{
							 child = new AddEdgeNode(copy, parent, index);
						}
						nextEdges.add(child);
						parent.addChild(child,false);
//						node1.addChild(index, child);
						
					}
				}
			}
			nodes1 = new LinkedList<AddNodeNode>();
//			System.out.println(nextEdges);
			while (!nextEdges.isEmpty()) {
				if (taskMonitor !=null && taskMonitor.isCancelled()) {
					return;
				}
				AddEdgeNode node2 = nextEdges.pollFirst();
				int edge = node2.getEdge();
				OrbitRepresentative or = node2.getOrbitRepresentative();
				OrbitRepresentative copy = new OrbitRepresentative(or);
				if (or.getEdges().contains(new Edge(edge, or.order() - 1))) {
//					System.out.println(node2);
					if (edge >= copy.order() - 2) {
						AddNodeNode child = new AddNodeNode(or, node2.getParent());
						nodes1.add(child);
						node2.getParent().replaceChild(node2, child);
//						node2.addChild(child, false);
					} else {
						node2.setEdge(edge + 1);
						nextEdges.addFirst(node2);
					}
				} else if (edge == copy.order() - 2) {
					/* Add the next layer of AddNodeNodes */
					AddNodeNode falsechild = new AddNodeNode(or, node2);
					nodes1.add(falsechild);
					node2.addChild(falsechild, false);
					copy.addEdge(edge, or.order() - 1);
//					copy.calculateSymmetry();
					AddNodeNode truechild = new AddNodeNode(copy, node2);
					nodes1.add(truechild);
					node2.addChild(truechild, true);
				} else {
					/* Add another layer of AddEdgeNodes */
					AddEdgeNode falsechild = new AddEdgeNode(or, node2, edge + 1);
					copy.addEdge(edge, or.order() - 1);
//					copy.calculateSymmetry();
					AddEdgeNode truechild = new AddEdgeNode(copy, node2, edge + 1);
					node2.addChild(falsechild, false);
					nextEdges.addLast(falsechild);
					node2.addChild(truechild, true);
					nextEdges.addLast(truechild);
				}
//				node2.prune();
			}
		}
		leaves = new HashSet<>();
		for (AddNodeNode n : nodes1) {
			if (taskMonitor !=null && taskMonitor.isCancelled()) {
				return;
			}
			OrbitRepresentative or = n.getOrbitRepresentative();
			if (leaves.add(or)) {
					counter++;
				if(taskMonitor!=null) {
					taskMonitor.setProgress((double)(counter)/OrbitIdentification.getNOrbitsTotal(order));
				}
				/*
				 * Break the OrbitRepresentative's symmetry and add the needed
				 * ConditionNodes for the restraints.
				 */
				List<Set<Integer>> cosetreps = or.getCosetreps();
				List<ConditionNode> conditionNodes = new ArrayList<ConditionNode>();
				for (int i = 0; i < cosetreps.size(); i++) {
					for (int j : cosetreps.get(i)) {
						ConditionNode cn = new ConditionNode(n, i + 1, j);
//						cn.insert(n);
						conditionNodes.add(cn);
					}
				}
//				ConditionNode.simplify(conditionNodes);
				for(ConditionNode cn: conditionNodes){
					cn.insert(n);
				}

			} else {
				/* Remove duplicate leaves. */
				n.remove();
				n.prune();
			}

		}
		/* Calculate the nodes' depths */
		root.updateDepth();
	}

	/**
	 * Returns this tree's registered TreeInterpreter.
	 * 
	 * @return this tree's TreeInterpreter.
	 */
	public TreeInterpreter getInterpreter() {
		return interpreter;
	}

	/**
	 * Registers a TreeInterpreter.
	 * 
	 * @param interpreter
	 *            The TreeInterpreter to be registered.
	 */
	public void setInterpreter(TreeInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	/**
	 * Gets this tree's root node.
	 * 
	 * @return The root node of this tree.
	 */
	public TreeNode getRoot() {
		return root;
	}

	/**
	 * Get a set containing all OrbitRepresentatives in leaves of this tree.
	 * 
	 * @return A Set containing this tree's leaves.
	 */
	public Set<OrbitRepresentative> getLeaves() {
		return leaves;
	}

	/**
	 * Writes this tree to file, in a form that can be read by the appropriate constructor of OrbitTree.
	 * @see OrbitTree#OrbitTree(String)
	 * @param filename The name of the file that will be saved.
	 */
	public void write(String filename) {
		try {
			PrintWriter ps = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
			ps.println(root.write());
			ps.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/**
	 * Reads an OrbitTree in from file. Files read by the write method can be read.
	 * @see OrbitTree#write(String)
	 * @param filename The name of the file that will be read.
	 */
	public OrbitTree(String filename) {
		try{
			File file = new File(filename);
			Scanner scanner = new Scanner(file);
			this.parseOrbitTreeFile(scanner);
		} catch (FileNotFoundException e) {
			System.err.println("File not found");
		}
		
	}
	
	/**
	 * Reads an OrbitTree in from file. Files read by the write method can be read.
	 * @see OrbitTree#write(String)
	 * @param filename The name of the file that will be read.
	 */
	public OrbitTree(URL url) {
		try {
			InputStream inputStream = url.openStream();
			Scanner scanner = new Scanner(inputStream);
			this.parseOrbitTreeFile(scanner);
		} catch (IOException e) {
			System.err.println("Couldn't read from resource");
		}		
	}
	
	
	public void parseOrbitTreeFile(Scanner scanner) {
		order = 0;
		String s = scanner.nextLine();
		OrbitRepresentative o = new OrbitRepresentative();
		root = new AddNodeNode(o,this );
		TreeNode tn = root;
		boolean leaf = true;
		leaves = new HashSet<>();
		while (scanner.hasNextLine()) {
			s = scanner.nextLine();
			String[] pieces = s.split(" ");
			if(pieces[0].charAt(0)=='/'){
				if(leaf){
					leaves.add(((AddNode)tn).getOrbitRepresentative());
					leaf = false;
				}
				tn = tn.parent;
			}else if (tn instanceof AddNodeNode) {
				leaf = true;
				AddNodeNode ann = (AddNodeNode) tn;
				int edge =Integer.parseInt(pieces[0]);
				o = new OrbitRepresentative(ann.getOrbitRepresentative());
				o.addNode(edge);
				if(o.order()>order)order=o.order();
				switch(pieces[1].charAt(0)){
				case 'n':
					tn=new AddNodeNode(o,ann);
					break;
				case 'e':
					tn = new AddEdgeNode(o,ann,Integer.parseInt(pieces[2]));
					break;
				case 'c':
					tn = new ConditionNode(tn, Integer.parseInt(pieces[2]), Integer.parseInt(pieces[3]));
					break;
				}
				ann.addChild(edge, tn);
			}else if(tn instanceof AddEdgeNode){
				leaf = true;
				AddEdgeNode ann = (AddEdgeNode) tn;
				boolean edge = Boolean.parseBoolean(pieces[0]);
				o = new OrbitRepresentative(ann.getOrbitRepresentative());
				if(edge){
					o.addEdge(o.order()-1, ann.getEdge());
				}
				switch(pieces[1].charAt(0)){
				case 'n':
					tn=new AddNodeNode(o,ann);
					break;
				case 'e':
					tn = new AddEdgeNode(o,ann,Integer.parseInt(pieces[2]));
					break;
				case 'c':
					tn = new ConditionNode(tn, Integer.parseInt(pieces[2]), Integer.parseInt(pieces[3]));
					break;
				}
				ann.addChild(tn, edge);
			}else if(tn instanceof ConditionNode){
				leaf = true;
				ConditionNode ann = (ConditionNode) tn;
				switch(pieces[0].charAt(0)){
				case 'n':
					tn=new AddNodeNode(o,ann);
					break;
				case 'e':
					tn = new AddEdgeNode(o,ann,Integer.parseInt(pieces[1]));
					break;
				case 'c':
					tn = new ConditionNode(tn, Integer.parseInt(pieces[1]), Integer.parseInt(pieces[2]));
					break;
				}
				ann.setChild(tn);
			}
		}
		scanner.close();
		root.updateDepth();
	}

	
	/**
	 * Prints the tree in human-readable form to the console.
	 */
	public void print(){
		root.printTree("");
	}
	
	/**
	 * Returns the largest graphlet order of the orbit representatives in this tree.
	 * @return The largest graphlet order in this tree.
	 */
	public int getOrder(){
		return order;
	}

	/**
	 * 
	 * @param order The largest graphlet order in this tree.
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	public void setTaskMonitor(TaskMonitor tm) {
		this.taskMonitor = tm;
	}
	
	public static void main(String[]args) {
		OrbitIdentification.readGraphlets(null, 6);
		OrbitTree ot = new OrbitTree(6);
	}
}
