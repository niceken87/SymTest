package com.viseo;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkuNameParser {
	/**
	 * Libellé à reconnaitre parmi les candidats
	 */
	String Libelle;
	/**
	 * Table des produits
	 */
	LinkedHashSet<HashMap<String, Object>> products;
	/**
	 * Table de correspondance enre les mots utilisés par le client et le mot à utiliser pour standardiser. L'ordre de remplacement suit celui du stockage
	 */
	LinkedHashMap<String, String> remplacements=new LinkedHashMap<String, String>();  

	HashMap<Character, Double> vLibelle; 

	HashSet<Candidat> candidates=new HashSet<Candidat>(); 

	LinkedHashSet<HashMap<String, Object>> libelles;

	Connection conn;

	public static final char UNKNOWNS='_';

	private LinkedHashMap<String, TreeSet<Double>> allowed=new LinkedHashMap<String, TreeSet<Double>>(); 

	public SkuNameParser(String libelleInconnu) {
		setLibelle(remplace(libelleInconnu));
	}

	public SkuNameParser() {
		// TODO Auto-generated constructor stub
	}

	public void setLibelle(String s){
		if (s!=null) {
			Libelle = s;
			vLibelle = vectorize(Libelle);
		}
	}
	public String getLibelle() {
		return Libelle;
	}
	/**
	 * 
	 * @param raws	liste de couples
	 * @param colSource	Chaine de caractère à remplacer
	 * @param colTarget Chaine à utiliser à la place
	 * @param append True add to existing pairs. Replace if already exists; False: Clear the previous pairs.
	 * @return
	 */
	public LinkedHashMap<String, String> setRemplacements(LinkedHashSet<HashMap<String, Object>> raws, String colSource, String colTarget, boolean append) {
		if (!(append))
			remplacements.clear();
		if (raws==null || raws.size()==0){
			Commun.warn("aucun remplacement à traiter.");
		}
		else {
			for (HashMap<String, Object> r:raws) {
				remplacements.put(r.get(colSource).toString(), r.containsKey(colTarget)?r.get(colTarget).toString():"");
			}
			Commun.log(remplacements.size()+" remplacements stockés.");
		}		
		return remplacements;
	}
	/**Récupère la liste de string à remplacer
	 * FIXME: Ajouter dans la base les codes pays (003, 002, etc. ) ->"", il faut tout simplement les supprimer des libellés
	 * @param colSource Nom de la colonne dans la table qui contient la chaine à trouver
	 * @param colTarget Nom de la colonne dans la table qui contient la chaine à mettre à la place.
	 * @return
	 */
	public LinkedHashMap<String, String> downloadRemplacements(String colSource, String colTarget) {
		if (conn==null) throw new NullPointerException("Aucune connexion fournie.");		
		remplacements.clear();
		try {
			Statement st=conn.createStatement();
			ResultSet rs = st.executeQuery("select "+colSource+" as Source,"+colTarget+" as Target from ListeRemplacements");
			int cpt = 0; 
			ResultSetMetaData rsmd = rs.getMetaData();
			while (rs.next()) {
				String so = rs.getString("Source");
				String ta = rs.getString("Target");
				remplacements.put(so, ta);
				cpt++;
			}
			rs.close();
			Commun.log(remplacements.size()+" remplacements téléchargés.");
		}
		catch(SQLException e) {
			Commun.error("Downloading substitutions strings has failed because of an SQL error");
			e.printStackTrace();
			remplacements.clear();
		}
		setLibelle(remplace(getLibelle()));
		return remplacements;
	}
	/**
	 * Standardise le libellé
	 * 1. Passe en capitale
	 * 2. Remplace tous les chaines de caractères associées à une remplacant, proposée par {@link #remplacements}
	 * 3. Applique les remplacements spéciaux, identifiés lors de l'analyse, afin de distinguer les infos de Volume, Degré et Pack.  
	 * @param s String à transformer. 
	 * @return Retourne la chaine  standardisée. 
	 */
	public String remplace(String s) {
		if (s==null) return null;
		String sol = s.toUpperCase();
		for (String k:remplacements.keySet()) {
			sol = sol.replace(k, remplacements.get(k));
		}
		sol = remplace_speciaux(sol);
		return sol;
	}
	/**Applique des remplacements particuliers. 
	 * A terme, il faudrait pouvoir saisir ce type de changement dans un fichier de config.
	 * @param s Chaine à standardiser
	 * @return Chaine standardisée;
	 */
	static public String remplace_speciaux(String s_) {
		String s = s_.replaceAll("\\b[p|P][\\d]+\\b", ""); //Retourne tous les mots commencant par P et suivi de chiffres, ca correspond à l'année de packaging.
		s = s.replaceAll("\\b\\d\\d\\d\\b", ""); //Retourne tous les mots composés de 3 chiffres, cela correspond à des codes PAYS. 
		
		Pattern p = Pattern.compile("_(\\d)+_CS(\\d)+"); //ex: _0700_CS6 ->0700ML X6
		Matcher m = p.matcher(s);
		if (m.find()) {
			String s2 =s.replace("_CS", "ML X");
			return s2;
		}

		p = Pattern.compile("(\\d)+/(\\d)+/(\\d)+(\\.(\\d)+)?"); //ex: 6/70/30 -> 6X 70CL 30%
		m = p.matcher(s);
		if (m.find()) {
			String s2 =s.substring(0, m.start())+s.substring(m.start(), m.end()).replaceFirst("/","X ").replace("/","CL ")+"%"+s.substring(m.end());
			return s2;
		}


		return s;
	}

	static public String acronymyse(String s_) {
		if (s_==null) return null;
		String s = s_.toUpperCase().trim();
		String[] ss =s.split(" ");
		String regex = "";
		for (String mot:ss) {
			String regex2="";
			char[] mot2 = mot.toCharArray();
			try {//Je garde les nombres
				Double chiffre = new Double(mot);
				regex = regex +"( |\\\\.)?("+mot+")?";
			}
			catch (NumberFormatException e){
				for (int i=0;i<mot2.length;i++) {
					if (i==0)  regex2=regex2+mot2[i];
					else regex2=regex2+"("+mot2[i]+")?";
				}

				if (regex.length()==0) regex = regex2;
				else if (mot.length()<3)  regex=regex+"( |\\.)?("+regex2+")?";
				else regex=regex+"( |\\.)?"+regex2;
			}
		}

		//Control
		Pattern p = Pattern.compile(regex);
		s="préfixes et JDLF2020 et suffixes";
		Matcher m = p.matcher(s);
		// si recherche fructueuse
		while(m.find()) {
			// affichage de la sous-chaîne capturée
			Commun.debug("Groupe : " + m.group());
		}

		return regex;
	}
	public final static HashMap<Character, Double> getInitialVector() {
		String chars ="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
		//String chars ="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		HashMap<Character, Double> m = new HashMap<Character, Double>();
		for (Character c0: chars.toUpperCase().toCharArray()) {
			m.put(c0, 0.0);
		}
		if (m.get(UNKNOWNS)==null)
			m.put(UNKNOWNS, 0.0);

		return m;		
	}

	static public final HashMap<Character, Double> vectorize(String s){
		HashMap<Character, Double> m = getInitialVector();
		return vectorize(s, m, true);
	}

	static public final HashMap<Character, Double> vectorize(String s, HashMap<Character, Double> m, boolean  normalize){		
		double n=0;
		double total = 0.0;
		char c; 
		for(char c_ : s.toUpperCase().toCharArray()) {
			if (c_!=' '){
				if (m.get(c_)!=null) c=c_; else c=UNKNOWNS;
				m.put(c, m.get(c)+1);
			}
		}
		//m.remove(UNKNOWNS);
		if (normalize) {
			for (Character c2: m.keySet()) {
				total = total + Math.pow(m.get(c2), 2);
			}
			total = Math.sqrt(total);
			for (Character c2: m.keySet()) {
				m.put(c2, m.get(c2)/total);
			}
		}
		return m;
	}

	public Double cosine(HashMap<Character, Double> v){
		if (vLibelle==null) return 0.0;
		double ps = 0.0;
		HashSet<Character> sete = new HashSet<Character>() {{ 
			addAll(vLibelle.keySet()); 
			addAll(v.keySet()); 
		}}; 
		for (Character c: sete) {
			ps=ps + (vLibelle.get(c)!=null?vLibelle.get(c):0.0)*(v.get(c)!=null?v.get(c):0.0);
		}
		return ps;
	}
	public Double cosine(String l){
		if (Libelle==null)
			return 0.0;
		else {
			HashMap<Character, Double> v2 = vectorize(l);
			return cosine(v2);
		}

	}

	private void addCandidate(Candidat c) {
		candidates.add(c);
	}

	static String toJson(LinkedHashMap<Candidat, Double> res) {
		if (res==null | res.size()==0) return "[]";

		String json ="[";
		for (Candidat l: res.keySet()) {
			json = json +"{'Label':'"+ l.getLibelle()+"', 'Score':"+res.get(l)+",";
			if (l.getId().startsWith(Commun.PREFIX_RCDS))
				json=json+" 'IdRCDS':'"+l.getId().substring(Commun.PREFIX_RCDS.length())+"'"; 
			else
				json=json+" 'IdSAP':'"+l.getId()+"'"; 
			json = json + "},";
		}
		return json.substring(0, json.length()-1)+"]";
	}

	static LinkedHashMap<Candidat, Double> getMeilleurs(TreeMap<Double, HashSet<Candidat>> score, Integer premiers){
		LinkedHashMap<Candidat, Double> res = new LinkedHashMap<Candidat, Double>();
		if (premiers==null || premiers<0 || score==null || score.size()==0)
			return res; 
		int cpt = premiers; 
		for (Double d : score.descendingKeySet()){
			HashSet<Candidat> s =score.get(d);
			for (Candidat s2: s) {
				res.put(s2, d);
				cpt=cpt-1; 
			}
			if (cpt<0)
				return res;
		}
		return res;	
	}

	public TreeMap<Double, HashSet<Candidat>> score(){	
		TreeMap<Integer, TreeMap<Double, HashSet<Candidat>>> sols = new TreeMap<Integer, TreeMap<Double, HashSet<Candidat>>>();
		for (int choix =0; choix<1;choix++) {
			TreeMap<Double, HashSet<Candidat>> sol = score(choix);
			double seuil =0.8; 
			SortedMap<Double, HashSet<Candidat>> filtres = sol.tailMap(seuil);
			while (filtres.size()<5 && seuil>0) {
				seuil = seuil-0.2; 
				filtres = sol.tailMap(seuil);
				if (filtres.size()>0 && filtres.lastKey()==0.0)
					filtres.clear();
			}
			Commun.debug(getLibelle() +"=>"+(filtres.size()>0?filtres.get(filtres.lastKey())+ "\t"+filtres.size()+" candidates(>"+String.format("%.2f",seuil)+")/"+sol.size()+"\t\t"+SkuNameParser.getMeilleurs(sol, 10):" No candidates (0/"+sol.size()+")"));
			sols.put(choix, sol); 
		}
		if (sols.size()>1)
			sols.put(2, SkuNameParser.merge(sols.get(0), sols.get(1)));
		if (sols.get(sols.lastKey()).size()>0){
			Commun.debug(getLibelle() +"=>"+sols.get(sols.lastKey()).get(sols.get(sols.lastKey()).lastKey())+"\t"+sols.get(sols.lastKey()).size()+" candidates\t\t"+SkuNameParser.getMeilleurs(sols.get(sols.lastKey()), 10));
		}
		else
			Commun.debug(getLibelle() +"=>"+sols.get(2));

		//System.out.println(SkuNameParser.getMeilleurs(sols.get(2),5)); //Récupère les 5 meilleurs candidats
		return sols.get(sols.lastKey());
	}
	public TreeMap<Double, HashSet<Candidat>> score(int mode){
		TreeMap<Double, HashSet<Candidat>> sol = new TreeMap<Double, HashSet<Candidat>>();
		if (Libelle ==null) return sol;
		for (Candidat s: candidates) {
			//Double d = cosine(s);
			Double d = 0.0; 

			if (mode == 1)
				d = getLevenshteinSimilarity(getLibelle(), s.getLibelle());
			else 
				d= cosine(s.getLibelle());
			HashSet<Candidat> s0 = sol.get(d);
			if (s0==null)
				s0 = new HashSet<Candidat>();
			s0.add(s);
			sol.put(d, s0);
		}
		return sol;	
	}

	static final TreeMap<Double, HashSet<Candidat>> merge(TreeMap<Double, HashSet<Candidat>>... scores) {
		TreeMap<Double, HashSet<Candidat>> sol = new TreeMap<Double, HashSet<Candidat>>();
		HashMap<Candidat, Double> sol1 = new HashMap<Candidat, Double>();
		double cptTotal = 0.0; 
		for (TreeMap<Double, HashSet<Candidat>> score: scores) {
			int cpt = 1; 			
			if (score.size()==0) { //Si l'une des liste  est vide, c'est qu'une métrique ne trouve rien de valide => On laisse tomber.
				sol.clear();
				return sol;
			}
			cptTotal = cptTotal + score.size();
			for (double d: score.keySet()) {
				HashSet<Candidat> s = score.get(d);
				for (Candidat s2: s) {
					Double pos =sol1.get(s2); 
					sol1.put(s2, ((pos==null)?0:pos) + cpt);
				}
				cpt++;					
			}			
		}
		for (Candidat s : sol1.keySet()) {
			Double sc =sol1.get(s)/cptTotal;
			HashSet<Candidat> s0 = sol.get(sc); 
			if (s0==null)
				s0 = new HashSet<Candidat>();
			s0.add(s);
			sol.put(sc, s0);
		}
		return sol;
	}
	public LinkedHashSet<HashMap<String, Object>> downloadProducts() throws SQLException {
		if (conn==null) throw new NullPointerException("Aucune connexion fournie.");
		Statement st=conn.createStatement();
		ResultSet rs = st.executeQuery("select * from Product");
		int cpt = 0; 
		ResultSetMetaData rsmd = rs.getMetaData();
		LinkedHashSet<HashMap<String, Object>> table = new LinkedHashSet<HashMap<String, Object>> ();
		while (rs.next()) {
			HashMap<String, Object> h = new HashMap<String, Object>();
			for (int i=1; i<=rsmd.getColumnCount();i++) {
				String name = rsmd.getColumnName(i);
				String s2 = rs.getString(name);
				h.put(name, s2);
			}				
			System.out.println(h);
			table.add(h);
			cpt++;
		}
		rs.close();

		return table; 
	}

	public LinkedHashSet<HashMap<String, Object>> downloadUnknownLibelles() throws SQLException {
		if (conn==null) throw new NullPointerException("Aucune connexion fournie.");
		Statement st=conn.createStatement();
		ResultSet rs = st.executeQuery("select 'UNKNWOWN' as Customer, LABEL as SKU from Product where Family like '%UNSPECIFIED%'");
		//ResultSet rs = st.executeQuery("select 'UNKNWOWN' as Customer, LABEL as SKU from Product");
		int cpt = 0; 
		ResultSetMetaData rsmd = rs.getMetaData();
		LinkedHashSet<HashMap<String, Object>> table = new LinkedHashSet<HashMap<String, Object>> ();
		while (rs.next()) {
			HashMap<String, Object> h = new HashMap<String, Object>();
			for (int i=1; i<=rsmd.getColumnCount();i++) {
				String name = rsmd.getColumnName(i);
				String s2 = rs.getString(name);
				h.put(name, s2);
			}				
			System.out.println(h);
			table.add(h);
			cpt++;
		}
		rs.close();

		return table; 
	}

	public LinkedHashSet<HashMap<String, Object>> getProducts() {
		return products;
	}
	public void disconnect() throws SQLException {
		if (conn!=null)
			conn.close();		
	}



	static LinkedHashSet<HashMap<String, Object>> filtre(LinkedHashSet<HashMap<String, Object>> table, String column, Object value) throws Exception {
		if (table == null) return null; 
		LinkedHashSet<HashMap<String, Object>> sol = new LinkedHashSet<HashMap<String, Object>> (); 
		for (HashMap<String, Object> r: table) {
			if (!r.containsKey(column)) {
				Commun.warn("Colonne inconnue de la table:"+column+". La table ne peut etre filtrée sur cette colonne.");
				return table;
			}
			Object c = r.get(column);
			if (value==null || c==null)
				sol.add(r);
			else if (value instanceof String && ((String) value).equals(c.toString()))
				sol.add(r);						
			else if (value.equals(c))
				sol.add(r);
		}

		return sol; 
	}

	/**
	 * Retourne tous les produits facturés au client donné
	 * Doit aller chercher dans la base une table produit qui renvoie les colonnes de float nommées Volume (en L), Strength (entre 0 et 100), et Pack (en unités 6, 12, 24). 
	 * @param clientId Si null, on prend tous les produits mentionnés au moins 1 fois (donc facturé au moins 1 fois).
	 * @return La tables des produits. 
	 * @throws Exception 
	 */
	public LinkedHashSet<HashMap<String, Object>> downloadInvoicedProducts(HashSet<String> clientIds, String colonne) throws Exception {
		String requete = "select case " + 
				"	when SalesTextFR is not null and trim(SalesTextFR)!='' then trim(SalesTextFR) " + 
				"	when SalesTextEN is not null and trim(SalesTextEN)!='' then trim(SalesTextEN)" + 
				"	when MaterialDescriptionEN is not null and trim(MaterialDescriptionEN)!='' then trim(MaterialDescriptionEN)" + 
				"	when MaterialDescriptionFR is not null and trim(MaterialDescriptionFR)!='' then trim(MaterialDescriptionFR)" + 
				"	else  trim(ProductSizeLabel) " + 
				"end as Label, " + 
				"MaterialVolumeSize as Volume,"+
				"AlcoolRate as Strength,"+
				"MaterialNumerator as Pack," +					
				"case " + 
				"when MaterialCode is not null then MaterialCode " + 
				"when AncienCode_RCDS is not null then CONCAT('"+Commun.PREFIX_RCDS+"', AncienCode_RCDS) " + 
				"else 'Unknown' end as Code " + 
				"from Product_SAP"; 
		if (clientIds!=null && clientIds.size()>0) {
			String clientsSQL="(";
			for (String clientId : clientIds) {
				clientsSQL=clientsSQL+"'"+clientId+"',";
			}
			clientsSQL=clientsSQL.substring(0, clientsSQL.length()-1)+")";
			requete = requete+" where SoldTo_CustomerCode in "+clientsSQL+" OR SoldTo_CustomerName in "+clientsSQL+" OR ShipTo_CustomerCode in "+clientsSQL+" OR ShipTo_CustomerName in "+clientsSQL;				
		}
		else
			Commun.warn("No Client ids given; All Products are going to be used.");
		requete = "select * from ("+requete+") t group by Label, Code, Volume, Strength, Pack";
		Commun.debug("Requete des produits concernés:\n\t"+requete);
		Statement s = conn.createStatement(); 
		ResultSet rs = s.executeQuery(requete);
		int cpt = 0; 
		ResultSetMetaData rsmd = rs.getMetaData();
		LinkedHashSet<HashMap<String, Object>> table = new LinkedHashSet<HashMap<String, Object>> ();
		while (rs.next()) {
			HashMap<String, Object> h = new HashMap<String, Object>();
			for (int i=1; i<=rsmd.getColumnCount();i++) {
				String name = rsmd.getColumnName(i);
				int t = rsmd.getColumnType(i);					 
				if (t==Types.NUMERIC | t == Types.REAL | t == Types.FLOAT | t == Types.DOUBLE | t==Types.DECIMAL)
				{
					Double d = rs.getDouble(name);
					if (rs.wasNull())
						d=null; 
					h.put(name, d);	
				}
				else
					h.put(name, rs.getString(name));
			}
			if (h.get("Label")==null) 
				if (h.get("Code")==null) 
					Commun.warn("1 extracted product does not have any label nor code. Not kept");
				else
					Commun.warn("1 extracted product does not have any label (code is "+h.get("Code")+"). Not kept");
			else if (h.get("Code")==null) 
				Commun.warn("1 extracted product does not have any Code (Label is "+h.get("Label")+"). Not kept");
			else
				table.add(h);
			cpt++;
		}
		rs.close();
		Commun.log(table.size()+" produits ont été facturés aux clients identifiés par "+clientIds.toString());
		setProducts(table);
		int nbCandidates=0;
		if (colonne!=null)
			if(getLibelle()!=null) {
				QualifiedSKU qs = new QualifiedSKU(remplace(getLibelle()), getAllowedValues("Volume"), null, null);
				nbCandidates = setCandidates(qs.filtre(getProducts()), Arrays.asList(colonne), "Code", true);
			}
			else {
				nbCandidates = setCandidates(getProducts(), Arrays.asList(colonne), "Code", true); 
			}
		return table; 

	}

	public static HashMap<String, Object> fromJSON(String json) {
		if (json==null || json.length()<=2) return null;
		json= json.replace("\r\n", ""); //get rid of carriages returns; 
		if (!(json.startsWith("{") && json.endsWith("}"))) return null; 

		HashMap<String, Object> sol = new HashMap<String, Object>();
		//get Values
		String r = "\\\"([^\\\"]+)\\\"[ ]*:[ ]*\\\"([^\\\"]+)\\\"";
		Pattern p = Pattern.compile(r);
		Matcher m = p.matcher(json);
		while (m.find()) {
			sol.put(m.group(1), m.group(2));
		}

		//Get sets
		r = "\\\"([^\\\"]+)\\\"[ ]*:[ ]*(\\[.+\\])";
		p = Pattern.compile(r);
		m = p.matcher(json);
		while (m.find()) {
			r = "\\\"([^\\\"]+)\\\"[ ]*:[ ]*(\\[.+\\])";
			Pattern p2 = Pattern.compile("\\\"([^\\\"]+)\\\"");
			Matcher m2 = p2.matcher(m.group(2));
			HashSet<String> liste = new HashSet<String>();
			while (m2.find()) {
				liste.add(m2.group(1));
			}
			sol.put(m.group(1), liste);			
		}
		return sol;

	}
	public int downloadLibelles() throws SQLException {

		Statement s = conn.createStatement(); 
		ResultSet rs = s.executeQuery("select top 10 * from Product");
		int cpt = 0; 
		while (rs.next()) {
			HashMap<String, Object> h = new HashMap<String, Object>();
			String s2 = rs.getString("Sku");
			h.put("SKU", s2);
			h.put("Customer", "Inconnu");
			System.out.println("Sku = " + s2);		
			libelles.add(h);
			cpt++;
		}
		rs.close();
		return cpt; 

	}
	public Connection connect(String url, String login, String password) throws SQLException {
		disconnect(); 
		Commun.debug("Tentative de connexion à la base : "+url);
		conn = DriverManager.getConnection(url, login, password);
		Commun.debug("Connexion à la base réussie.");

		return conn; 
	}


	static public LinkedHashSet<HashMap<String, Object>> readCSV(String filepath, String sep) throws FileNotFoundException {
		Scanner scanner = new Scanner(new File(filepath));
		Scanner dataScanner=null; 
		String ligneEntete=null;
		int index = 0;
		LinkedHashSet<HashMap<String, Object>> sol = new LinkedHashSet<HashMap<String, Object>>();
		ArrayList<String> labels = new ArrayList<String>();

		while (scanner.hasNextLine()) {
			String ligne = scanner.nextLine(); 
			dataScanner = new Scanner(ligne);
			dataScanner.useDelimiter(sep);
			if (index==0) {
				ligneEntete = ligne;
				while (dataScanner.hasNext()) {
					String data = dataScanner.next();
					data=data.trim();
					if ((data.startsWith("\"")) && (data.endsWith("\"")))
						data = data.substring(1, data.length()-1);
					labels.add(data);					
				}
			}

			else {
				HashMap<String, Object> raw = new HashMap<String, Object>();
				int cpt = 0;
				while (dataScanner.hasNext()) {
					String data = dataScanner.next();
					data = data.trim();
					if ((data.startsWith("\"")) && (data.endsWith("\"")))
						data = data.substring(1, data.length()-1);
					if (data.length()>0)
						try {
							raw.put(labels.get(cpt), Double.parseDouble(data));					
						}					
					catch(NumberFormatException e2) {

						raw.put(labels.get(cpt), data);					
					}
					cpt++;
				}
				sol.add(raw);
			}
			index++;
		}

		scanner.close();
		return sol;
	}

	public int setCandidates(Set<HashMap<String, Object>> products, List<String> cols, String colId, boolean reset) throws NullPointerException {
		int cpt = 0; 
		if (reset) {
			candidates.clear();
			allowed.clear();
		}
		for (HashMap<String, Object> raw: products) {
			String r =" "; 
			for (String c : cols) {
				if (!raw.keySet().contains(c)) throw new NullPointerException("Colonne "+c+" n'est pas dans les attributs de l'individu. Le candidat ne peut être créé.");
				r = r+raw.get(c)+ " ";
			}
			addCandidate(new Candidat(r.trim(), raw.get(colId).toString()));
			cpt++;
		}
		if (candidates.size()>0)
			Commun.log(cpt+" candidats inserés ("+candidates.toArray()[0]+",...)");
		else 
			Commun.log("Aucun candidat inséré.");
		return cpt; 
	}

	void setProducts(LinkedHashSet<HashMap<String, Object>> p) {
		if (products!=null)
			products.clear();
		if (allowed!=null)
			allowed.clear();
		products = p;
		for (HashMap<String, Object> raw: products) {
			addAllowedValue("Pack", (Double) raw.get("Pack"));
			addAllowedValue("Volume", (Double) raw.get("Volume"));
			addAllowedValue("Strength", (Double) raw.get("Strength"));
		}
		Commun.log(products.size()+" produits insérés.");
	}

	/**Return a similarity bw two strings : 1 is the best, 0 the worst. 
	 * It is based on Levenshtein distance : 0 they are the same, N the worst value, where N is the longest length of both strings
	 * @param a_
	 * @param b_
	 * @return
	 */
	public final static double getLevenshteinSimilarity(String a_, String b_) {
		char[] a= a_.toCharArray();
		char[] b= b_.toCharArray();
		Integer[][] d = new Integer[a.length+1][b.length+1];
		int subs = 0; 
		for (int i=0;i<=a.length;i++)
			d[i][0]=i;
		for (int j=0;j<=b.length;j++)
			d[0][j]=j;
		for (int j=1;j<=b.length;j++)
			for (int i=1;i<=a.length;i++) {		
				if (a[i-1]==b[j-1]) subs = 0; else subs =1; 
				d[i][j]=Math.min(
						Math.min(d[i-1][j] + 1, 
								d[i][j-1]+1
								), 
						d[i-1][j-1]+subs);
			}
		return 1.0-(d[a.length ][b.length]/(Math.max(a_.length(), b_.length())+0.0));
	}

	static double Levenstein(String a, String b){
		Commun.log(a.length()+" "+b.length());
		if (a.length()==0)
			return b.length();
		if (b.length()==0)
			return a.length();
		if (a.substring(0,1)==b.substring(0,1))
			return Levenstein(a.substring(1), b.substring(1));
		return 1 + Math.min(Levenstein(a.substring(1), b), Math.min(Levenstein(a, b.substring(1)), Levenstein(a.substring(1), b.substring(1))));
	}
	private TreeSet<Double> getAllowedValues(String key) {
		// TODO Auto-generated method stub
		return allowed.get(key);
	}
	private void addAllowedValue(String key, Double value) {
		if (value==null) return;
		TreeSet<Double> s = allowed.get(key);
		if (s==null)
			s = new TreeSet<Double>();
		s.add(value);
		if (value!=null)
			allowed.put(key, s);

	}
	/**
	 * 
	 * @param libelleInconnu Label à identifier
	 * @param clientsId Liste des ids clients qui ont pu utiliser ce label (confronté aux champs Soldto_customerCode ou ShipTo_CustomerCode
	 * @param dbUrl URL de la database
	 * @param dbLogin Login de la Database
	 * @param dbPwd Pws de la database
	 * @return retourne un Json contenant {"Libelle": @libelleInconnu, "candidats":[{"Label": Libellé du produit candidat1, "iDSAP": Id SAP CAP1 du produit1, "Score":Score du produit1}, avec produit2, produit3,...]}
	 */
	static final String score(String libelleInconnu, HashSet<String> clientsId, String dbUrl, String dbLogin, String dbPwd) {

		String jsonSol = null; 


		if (libelleInconnu==null || libelleInconnu.trim()=="") throw new NullPointerException("Libellé est vide.");
		try {
			Commun.log("Label to identify: "+libelleInconnu);		
			Commun.log("Client Ids: "+clientsId);

			SkuNameParser smp2 = new SkuNameParser(libelleInconnu); //Libellé à matcher. 
			//smp2.setRemplacements(listeRemplacements, "Source", "Target", false);

			smp2.connect(dbUrl, dbLogin, dbPwd); //Connexion à la base de données contenant : Produits, Factures, Clients, Transformations? 

			smp2.downloadRemplacements("Source", "Target");
			smp2.downloadInvoicedProducts(clientsId, "Label"); //Récupère de la base les produits associés à un client (Si client = null, on prend tous les produits)  et Utilise la colonne "Label" comme candidats au matching
			smp2.disconnect();

			TreeMap<Double, HashSet<Candidat>> scores = smp2.score(); //Score chaque candidat
			LinkedHashMap<Candidat, Double> sol = SkuNameParser.getMeilleurs(scores,5); //Récupère les 5 meilleurs candidats


			jsonSol = SkuNameParser.toJson(sol); //Formate le résultat en json si nécessaire. 
			jsonSol = "{'Libelle':'"+libelleInconnu+"', 'candidats':"+jsonSol+"}";
			jsonSol = jsonSol.replace("'","\"");
		}
		catch(SQLException e){
			System.err.println("Echec lors de l'accès ou la lecture de la base.\n");
			e.printStackTrace();
		}
		catch(Exception e){
			System.err.println("Echec lors de la tentative de filtrage.\n");
			e.printStackTrace();
		}

		return jsonSol; 
	}

	public static final String score(String fromJson, String dbUrl, String dbLogin, String dbPwd) {
		HashMap<String, Object> paires = fromJSON(fromJson);		
		
		//récupère le libellé à parser dans le JSON
		String libelleInconnu = (String) paires.get("ProduitCandidat");
		if (libelleInconnu==null || libelleInconnu.trim().length()==0)
			throw new NullPointerException("Aucun produitCandidat fourni dans le json"); 
		
		HashSet<String> clientIds = new HashSet<String>();
		if (paires.get("CustomerCodeSAP")!=null)
			clientIds.addAll((HashSet<String>) paires.get("CustomerCodeSAP"));
		else
			Commun.warn("Aucune liste de CustomerCodeSAP founie.");
		if (paires.get("Customer")!=null)
			clientIds.add((String) paires.get("Customer"));
		else
			Commun.warn("Aucune Customer founi.");

		String jsonResult = score(libelleInconnu, clientIds, dbUrl, dbLogin, dbPwd);
		return jsonResult; 		
	}
	
	static void main2(String[] args) throws SQLException, IOException {
		String dbUrl = null, dbLogin =null, dbPwd = null; 
		FileWriter fOut= null; 
		String libelleInconnu =null;
		String clientId = null; 

		LinkedHashSet<HashMap<String, Object>> listeLibelles = null; 
		LinkedHashSet<HashMap<String, Object>> listeProduits = null; 
		LinkedHashSet<HashMap<String, Object>> listeRemplacements = null; 
		HashSet<String> clientIds = new HashSet<String>();
		int cpt = 0; 
		for (int i= 0; i <args.length;i++) {
			if (args[i].toLowerCase().trim().equals("-ls")) {
				i++;
				File f = new File(args[i]);
				listeLibelles = readCSV(args[i],";");
			}
			else if (args[i].toLowerCase().trim().equals("-c"))	{
				i++;
				clientIds.add(args[i].trim());
			}		
			else if (args[i].toLowerCase().trim().equals("-l"))	{
				i++;
				libelleInconnu = args[i].trim();
			}		
			else if (args[i].toLowerCase().trim().equals("-p"))	{
				i++;
				listeProduits = readCSV(args[i],";");
			}		
			else if (args[i].toLowerCase().trim().equals("-k"))	{
				i++;
				listeRemplacements = readCSV(args[i],";");
			}
			else if (args[i].toLowerCase().trim().equals("-o"))	{
				i++;
				fOut = new FileWriter(new File(args[i]));
				fOut.write("Label;Volume;Degre;Pack;Remains;Initial\n");
			}
			else if (args[i].toLowerCase().trim().equals("-dblogin"))	{
				i++;
				dbLogin= args[i];
			}
			else if (args[i].toLowerCase().trim().equals("-dburl"))	{
				i++;
				dbUrl=args[i];
			}
			else if (args[i].toLowerCase().trim().equals("-dbpwd"))	{
				i++;
				dbPwd= args[i];
			}
		}
		HashMap<String, String> lls = getLabelledLabels(dbUrl, dbLogin, dbPwd);
		clientIds.clear();
		for (String l : lls.keySet()) {
			String jsonResult = score(l, clientIds, dbUrl, dbLogin, dbPwd);
			HashMap<String, Object> paires = fromJSON(jsonResult);

			Commun.log("\n\nl:"+l+" \n\t("+lls.get(l)+")\n\t->"+jsonResult);
		}
	}

	private static HashMap<String, String> getLabelledLabels(String dbUrl, String dbLogin, String dbPwd) throws SQLException {
		Connection conn = DriverManager.getConnection(dbUrl, dbLogin, dbPwd);

		Statement st=conn.createStatement();
		ResultSet rs = st.executeQuery("select * from Product");
		ResultSetMetaData rsmd = rs.getMetaData();
		HashMap<String, String>table = new HashMap<String, String> ();
		while (rs.next()) {
			String cle=null; 
			String val=null; 
			for (int i=1; i<=rsmd.getColumnCount();i++) {
				String name = rsmd.getColumnName(i);
				if (name.equals("KeyProductLink"))
					cle = rs.getString(name);
				else if (name.equals("Label"))
					val = rs.getString(name);

			}

			if (cle!=null && val!=null)
				table.put(cle, val);	
		}
		rs.close();
		conn.close();

		return table; 
	}

	static final void main3(String[] args) throws IOException {
		String dbUrl = null, dbLogin =null, dbPwd = null; 
		FileWriter fOut= null; 
		String libelleInconnu =null;
		String clientId = null; 

		LinkedHashSet<HashMap<String, Object>> listeLibelles = null; 
		LinkedHashSet<HashMap<String, Object>> listeProduits = null; 
		LinkedHashSet<HashMap<String, Object>> listeRemplacements = null; 
		HashSet<String> clientIds = new HashSet<String>();
		int cpt = 0; 
		for (int i= 0; i <args.length;i++) {
			if (args[i].toLowerCase().trim().equals("-ls")) {
				i++;
				File f = new File(args[i]);
				listeLibelles = readCSV(args[i],";");
			}
			else if (args[i].toLowerCase().trim().equals("-c"))	{
				i++;
				clientIds.add(args[i].trim());
			}		
			else if (args[i].toLowerCase().trim().equals("-l"))	{
				i++;
				libelleInconnu = args[i].trim();
			}		
			else if (args[i].toLowerCase().trim().equals("-p"))	{
				i++;
				listeProduits = readCSV(args[i],";");
			}		
			else if (args[i].toLowerCase().trim().equals("-k"))	{
				i++;
				listeRemplacements = readCSV(args[i],";");
			}
			else if (args[i].toLowerCase().trim().equals("-o"))	{
				i++;
				fOut = new FileWriter(new File(args[i]));
				fOut.write("Label;Volume;Degre;Pack;Remains;Initial\n");
			}
			else if (args[i].toLowerCase().trim().equals("-dblogin"))	{
				i++;
				dbLogin= args[i];
			}
			else if (args[i].toLowerCase().trim().equals("-dburl"))	{
				i++;
				dbUrl=args[i];
			}
			else if (args[i].toLowerCase().trim().equals("-dbpwd"))	{
				i++;
				dbPwd= args[i];
			}
		}

		//main2(args);
		String fromJson = "{\r\n \"ProduitCandidat\" : \"PIPO BINGO\",\r\n \"Customer\" : \"HEINEMANN-HEINEMANN-YYYYMM.XLSX\",\r\n \"CustomerCodeSAP\" : [\r\n \"1000046396\",\r\n \"1000049702\",\r\n \"1000049713\",\r\n \"1000049714\",\r\n \"1000051234\",\r\n \"1000052086\",\r\n \"1000053266\",\r\n \"1000057038\",\r\n \"1000058574\"\r\n ]\r\n}";
		fromJson="{  \"ProduitCandidat\" : \"REMY LOUIS XIII GIFT PACK 3X70CL\",  \"Customer\" : \"HEINEMANN-HEINEMANN-YYYYMM.XLSX\",  \"CustomerCodeSAP\" : [\"0010024584\"]}";
		fromJson="{  \"ProduitCandidat\" : \"APEROL 11% 6X0.7\",  \"Customer\" : \"LOTTE-SG.XLSX\",  \"CustomerCodeSAP\" : [ \"0010024636\",\"0010024641\"]}";
		fromJson="{  \"ProduitCandidat\" : \"REMY LOUIS XIII GIFT PACK 3X70CL\",  \"Customer\" : \"DDF-A&E-YYYYMM.XLSX-DDF\",  \"CustomerCodeSAP\" : [ \"0010024364\",\"0010024585\",\"0010024034\"]}";
		fromJson="{\"ProduitCandidat\" : \"COUNTREAU BLOOD ORANGE 12/700ML\",\"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX\",\"CustomerCodeSAP\" :  [\"0010024347\",\"0010024358\",\"0010024377\",\"0010024486\",\"0010024105\",\"0010024106\",\"0010024563\",\"0010024596\",\"0010024049\",\"0010024051\",\"0010024345\",\"0010024345\",\"0010024391\",\"0010024391\",\"0010024404\",\"0010024404\",\"0010024173\",\"0010024175\",\"0010024272\",\"0010024275\",\"0010024315\",\"0010024580\",\"0010024581\",\"0010024583\",\"0010024598\"]}";
		fromJson="{\"ProduitCandidat\" : \"LOUIS XIII 40% 175CL\"}";
		//Parse le Json
		HashMap<String, Object> paires = fromJSON(fromJson);		
		//récupère le libellé à parser dans le JSON
		libelleInconnu = (String) paires.get("ProduitCandidat");

		//Récupère les différents ids du customer dans le JSON, pour ne conserver que les produits achetés par ces clients

		if (paires.get("CustomerCodeSAP")!=null)
			clientIds.addAll((HashSet<String>) paires.get("CustomerCodeSAP"));
		if (paires.get("Customer")!=null)
			clientIds.add((String) paires.get("Customer"));
		Commun.log("Version du 7 Mai 2021 - 10H16");
		String jsonResult = score(libelleInconnu, clientIds, dbUrl, dbLogin, dbPwd);
		Commun.log(jsonResult);
	}
	
	/**
	 * @param args
	 * @throws IOException
	 * @throws SQLException
	 */
	public static void main(String[] args) throws IOException, SQLException, Exception {
		main3(args);
		if (true) return;

		String dbUrl = null, dbLogin =null, dbPwd = null; 
		FileWriter fOut= null; 
		String libelleInconnu =null;
		String clientId = null; 

		LinkedHashSet<HashMap<String, Object>> listeLibelles = null; 
		LinkedHashSet<HashMap<String, Object>> listeProduits = null; 
		LinkedHashSet<HashMap<String, Object>> listeRemplacements = null; 
		HashSet<String> clientIds = new HashSet<String>();
		int cpt = 0; 
		for (int i= 0; i <args.length;i++) {
			if (args[i].toLowerCase().trim().equals("-ls")) {
				i++;
				File f = new File(args[i]);
				listeLibelles = readCSV(args[i],";");
			}
			else if (args[i].toLowerCase().trim().equals("-c"))	{
				i++;
				clientIds.add(args[i].trim());
			}		
			else if (args[i].toLowerCase().trim().equals("-l"))	{
				i++;
				libelleInconnu = args[i].trim();
			}		
			else if (args[i].toLowerCase().trim().equals("-p"))	{
				i++;
				listeProduits = readCSV(args[i],";");
			}		
			else if (args[i].toLowerCase().trim().equals("-k"))	{
				i++;
				listeRemplacements = readCSV(args[i],";");
			}
			else if (args[i].toLowerCase().trim().equals("-o"))	{
				i++;
				fOut = new FileWriter(new File(args[i]));
				fOut.write("Label;Volume;Degre;Pack;Remains;Initial\n");
			}
			else if (args[i].toLowerCase().trim().equals("-dblogin"))	{
				i++;
				dbLogin= args[i];
			}
			else if (args[i].toLowerCase().trim().equals("-dburl"))	{
				i++;
				dbUrl=args[i];
			}
			else if (args[i].toLowerCase().trim().equals("-dbpwd"))	{
				i++;
				dbPwd= args[i];
			}
		}
		//main2(args);
		String fromJson = "{\r\n \"ProduitCandidat\" : \"PIPO BINGO\",\r\n \"Customer\" : \"HEINEMANN-HEINEMANN-YYYYMM.XLSX\",\r\n \"CustomerCodeSAP\" : [\r\n \"1000046396\",\r\n \"1000049702\",\r\n \"1000049713\",\r\n \"1000049714\",\r\n \"1000051234\",\r\n \"1000052086\",\r\n \"1000053266\",\r\n \"1000057038\",\r\n \"1000058574\"\r\n ]\r\n}";
		fromJson="{  \"ProduitCandidat\" : \"REMY LOUIS XIII GIFT PACK 3X70CL\",  \"Customer\" : \"HEINEMANN-HEINEMANN-YYYYMM.XLSX\",  \"CustomerCodeSAP\" : [\"0010024584\"]}";
		fromJson="{  \"ProduitCandidat\" : \"APEROL 11% 6X0.7\",  \"Customer\" : \"LOTTE-SG.XLSX\",  \"CustomerCodeSAP\" : [ \"0010024636\",\"0010024641\"]}";
		fromJson="{  \"ProduitCandidat\" : \"REMY LOUIS XIII GIFT PACK 3X70CL\",  \"Customer\" : \"DDF-A&E-YYYYMM.XLSX-DDF\",  \"CustomerCodeSAP\" : [ \"0010024364\",\"0010024585\",\"0010024034\"]}";
		fromJson="{\"ProduitCandidat\" : \"COUNTREAU BLOOD ORANGE 12/700ML\",\"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX-DUFRY\",\"ProductSourceCode\" : \"3035540008282\",\"CustomerCodeSAP\" : [\"0010024347\",\"0010024358\",\"0010024377\",\"0010024486\",\"0010024105\",\"0010024106\",\"0010024563\",\"0010024596\",\"0010024049\",\"0010024051\",\"0010024345\",\"0010024345\",\"0010024391\",\"0010024391\",\"0010024404\",\"0010024404\",\"0010024173\",\"0010024175\",\"0010024272\",\"0010024275\",\"0010024315\",\"0010024580\",\"0010024581\",\"0010024583\",\"0010024598\"]}";
		//Parse le Json
		HashMap<String, Object> paires = fromJSON(fromJson);		
		//récupère le libellé à parser dans le JSON
		libelleInconnu = (String) paires.get("ProduitCandidat");

		//Récupère les différents ids du customer dans le JSON, pour ne conserver que les produits achetés par ces clients

		if (paires.get("CustomerCodeSAP")!=null)
			clientIds.addAll((HashSet<String>) paires.get("CustomerCodeSAP"));
		if (paires.get("Customer")!=null)
			clientIds.add((String) paires.get("Customer"));
		Commun.log("Version du 6 Mai 2021 - 10H16");
		clientIds.clear();

		HashMap<String, String> idsTrouves = new HashMap<String, String>(); 
		String[] ls = {"R.MARTIN VSOP 40% 1000ML","REMY MARTIN VSOP 1000ML","RM VSOP 100CL","COINTREAU 100CL SHAKER","COINTREAU 1L SHAKER","CTR 1L SHAKER PACK","COINTREAU LIQUEUR SHAKER FREE 1L","LOUIS XIII TIME COLLECTION 700ML","LOUIS XIII TIME COLLECTION THE ORIGIN 70CL","LOUIS XIII TIME COLLECTION 0,7L","LOUIS XIIII TC 70CL","LOUIS XIIII TC II 70C","LOUIS XIII TIME COLLECTION 2 70CL","LOUIS XIII TIME COLLECTION 2 700ML","BOTANIST ISLAY GIN PLANTER PACK 100CLS 46","BOTANIST ISLAY GIN 46% 1L PLANTER","THE BOTANIST GIN PLANTER PCK 100CL 46% ","THE BOTANIST ISLAY DRY GIN 1L PLANTER","RM XO CANNES EDITION 0.7L 40% GP","REMY M XO CANNES EDITION 0.7L 40% GP","REMY MARTIN XO CANNES 700ML","REMY MARTIN XO CANNES 2020 0,7L","REMY MARTIN XO CNS 70CL 40°","REMY MARTIN XO CNY 40% 700ML","RÉMY MARTIN XO CNY 2020 40% 0.7L","REMY MARTIN XO CHINESE NEW YEAR 70CL","REMY XO CNY 2019 LE 40% 70CL","R MARTIN XO STEAVEN RICHARD 40% 700ML","REMY MARTIN XO STEAVEN RICHARD 40% 70CL","RM STEAVEN RICHARD XO 70CL 40%","RM S.RICHARD XO 70CL 40%"};
		//déclenche la recherche des produits vendus au clients @clientIds puis compare le label de @LibelleInconnu
		int cpt2 = 0; 
		for (String lib: ls) {
			String jsonResult = score(lib, clientIds, dbUrl, dbLogin, dbPwd);
			Commun.log(jsonResult);
			String chap = "\"IdSAP\":";
			int a = jsonResult.indexOf(chap);
			if (a>0) {
				String ss = jsonResult.substring(a+chap.length());
				String idSap = ss.substring(0, ss.indexOf(","));
				System.out.println(cpt2+"/"+ls.length+": "+lib+"->"+idSap);
				idsTrouves.put(lib, idSap+"\t"+jsonResult);
			}
			else
				Commun.warn("No code for "+lib);
			cpt2++;
		}		
		Commun.log("\n"+idsTrouves.toString().replaceAll("=", "\t").replaceAll("]}, ","]}\n"));
		if (true) return;

		SkuNameParser smp=new SkuNameParser();
		smp.libelles = listeLibelles;
		smp.connect(dbUrl, dbLogin, dbPwd);
		smp.libelles = smp.downloadUnknownLibelles();

		//smp.setProducts(listeProduits);
		smp.downloadInvoicedProducts(clientIds, "Label");
		smp.downloadRemplacements("Source", "Target");
		smp.setRemplacements(listeRemplacements, "Source", "Target", true);
		FileWriter fQualifiedLlibelles = new FileWriter(new File("C:\\Users\\MathieuBarrault\\OneDrive - VISEO Groupe\\projets\\remyCointreau\\Libelles_qualifies.txt"));
		fQualifiedLlibelles.write("Label;Volume;Degre;Pack;Remains;Initial\n");

		for (HashMap<String, Object> r2: smp.libelles) {
			String initial = (String) r2.get("SKU");
			System.out.println("---------------------------------------------------------------");
			System.out.println("Label inconnu:"+initial);					

			QualifiedSKU qs = new QualifiedSKU(smp.remplace(initial), null, null, null);
			System.out.println(qs);


			if (fQualifiedLlibelles!=null)
				fQualifiedLlibelles.write(qs.toCSV(";")+";"+initial+"\n");
			smp.setLibelle(qs.Remains);
			int nbCandidates = smp.setCandidates(qs.filtre(smp.getProducts()), Arrays.asList("Label"), "Code", true);
			if (nbCandidates>0) {
				TreeMap<Double, HashSet<Candidat>> sol = smp.score();
			}
			else
				System.out.println(smp.getLibelle() +"=> No product with this qualification");
		}
		fQualifiedLlibelles.close();

		/*
		for (String r2: smp.candidates) {
			String attempt0= r2;
			String attempt =smp.remplace(attempt0); 
			smp.setLibelle(attempt);
			QualifiedSKU qs = new QualifiedSKU(attempt, smp.getAllowedValues("Volume"), smp.getAllowedValues("Strength"),smp.getAllowedValues("Pack"));
			System.out.println(qs);
			if (fOut!=null)
					fOut.write(qs.toCSV(";")+";"+r2+"\n");
		}
		if (fOut!=null)
			fOut.close();
		 */
	}

}