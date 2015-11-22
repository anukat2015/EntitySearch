package relation_linking;

import java.io.*;
import java.util.*;

public class DBPediaOntologyExtractor {

	private ArrayList<String> listOfRelations = new ArrayList<String>();

	public DBPediaOntologyExtractor(String filePath) throws FileNotFoundException, IOException, ClassNotFoundException {

		File dbPediaStore = new File("DBPediaStore");

		if (!dbPediaStore.exists() || dbPediaStore.isDirectory()) {
			try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
				String line;
				String relation = new String();
				while ((line = br.readLine()) != null) {
					if (line.indexOf("<http://dbpedia.org/ontology/") != -1) {
						relation = getRelation(line);

						if (!listOfRelations.contains(relation) && !relation.isEmpty()) {
							listOfRelations.add(relation);
						}
					}
				}
			}
			
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("DBPediaStore"));
			oos.writeObject(listOfRelations);
			oos.flush();
			oos.close();
		} else {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream("DBPediaStore"));
			listOfRelations = (ArrayList<String>) ois.readObject();
			ois.close();
		}
	}

	private String getRelation(String line) {
		String ontologyLink = line.substring(line.indexOf("<"), line.indexOf(">") + 1);
		return ontologyLink.substring(ontologyLink.lastIndexOf("/") + 1, ontologyLink.indexOf(">"));
	}

	public ArrayList<String> getDBPediaRelations() {
		return this.listOfRelations;
	}

	public void closeDBPediaOntologyExtractor() {
		listOfRelations = null;
	}

}
