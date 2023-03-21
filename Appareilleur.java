package com.viseo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

public class Appareilleur {

	public static int NB_RESULTATS = 10;
	public static double SCORE_MIN= 5000;
	HashMap<String, HashSet<String>> motsDunProduit = new HashMap<String, HashSet<String>>();
	private HashMap<String, String> libelleProduits=new HashMap<String, String>(); 
	HashMap<String, HashMap<Character, Double>> vecteurDunProduit = new HashMap<String, HashMap<Character, Double>>();

	public static final void setLimites(int nbResultatsMax, double scoreMin) {
		NB_RESULTATS=Math.max(1, nbResultatsMax);
		SCORE_MIN = scoreMin;
	}
	/**
	 * Un produit est défini par un ou plusieurs labels.
	 * @param ss
	 */
	public void ajouteProduit(String codeProduit, String...ss) {
		if (codeProduit==null) return;

		for (String s : ss) {
			ajouteProduit(codeProduit, s);
		}	
	}

	public Double cosine(HashMap<Character, Double> v2, HashMap<Character, Double> v){
		if (v2==null || v==null) return 0.0;
		double ps = 0.0;
		double d2, d, n=0, n2=0; 
		HashSet<Character> sete = new HashSet<Character>() {{ 
			addAll(v2.keySet()); 
			addAll(v.keySet()); 
		}}; 
		for (Character c: sete) {
			d2 = (v2.get(c)!=null?v2.get(c):0.0);
			d = (v.get(c)!=null?v.get(c):0.0);
			ps=ps + d2*d;
			n = n + (d*d); 
			n2 = n2 + (d2*d2); 			
		}
		return ps/(Math.sqrt(n*n2));
	}

	public void ajouteProduit(String codeProduit, String s) {
		if (codeProduit==null) return;

		if (s!=null && s.trim().length()>0) {
			HashSet<String> mots =getMots(codeProduit); 
			mots = recupereMots(s, mots);		
			setMots(codeProduit, mots);
		}
	}

	private void setMots(String codeProduit, HashSet<String> mots) {
		if (mots==null || mots.size()==0) {
			Commun.warn("Code produit "+codeProduit+" n'a pas de mot à insérer.");
			return; 
		}
		motsDunProduit.put(codeProduit, mots);

	}
	public HashSet<String> getMots(String codeProduit){
		return motsDunProduit.get(codeProduit);
	}

	public String getMotsConcantenes(String codeProduit){
		String sol = new String(); 
		String libelle = libelleProduits.get(codeProduit);
		if (libelle!=null)
			sol = libelle; 
		else
			Commun.warn(codeProduit+" n'a pas de libellé.");
		/*affichage des mots matchés pour Debugage
		sol =sol  + "->";
		HashSet<String> mots = motsDunProduit.get(codeProduit);
		for (String s: mots)
			sol = sol + s +" ";
		sol = sol.trim(); 
		 */
		return sol; 

	}
	public static HashSet<String> recupereMots(String s_, HashSet<String> sol){
		if (sol==null)
			sol = new HashSet<String>();		
		if (s_==null) return sol; 
		String s = s_.trim().toUpperCase();
		String[] ss = s.split("[ ]|_");
		for (String mot : ss) {
			if (!mot.matches("^\\d+.*") & mot.trim().length()>1) //Mot ne doit pas commencer par un chiffre et doit avoir au moins 2 caractèress
				sol.add(mot.trim());
		}
		return sol; 
	}

	public HashMap<String, Double> getRarete() {
		HashMap<String, Double> sol = new HashMap<String, Double>();

		for (HashSet<String> ms: motsDunProduit.values()){
			for (String m : ms)
				sol.put(m, ((sol.get(m)==null)?0:sol.get(m))+1);
		}
		Double mx = 0.0; 
		for (Double d: sol.values()) 
			mx = Math.max(d, mx);
		if (mx == 0)
			return sol; 
		else
			System.out.println("Max d'occurences:"+mx);

		for (String m: sol.keySet())
			sol.put(m, Math.pow(1-sol.get(m)/mx,2));

		return sol; 
	}

	public int peuple(String dbUrl, String dbLogin, String dbPwd, HashSet<String> clientIds, Double Volume, Double Degre, Double Pack) throws SQLException {
		String libelle = "\ncase " + 
				"	when SalesTextFR is not null and trim(SalesTextFR)!='' then trim(SalesTextFR) " + 
				"	when SalesTextEN is not null and trim(SalesTextEN)!='' then trim(SalesTextEN)" + 
				"	when MaterialDescriptionEN is not null and trim(MaterialDescriptionEN)!='' then trim(MaterialDescriptionEN)" + 
				"	when MaterialDescriptionFR is not null and trim(MaterialDescriptionFR)!='' then trim(MaterialDescriptionFR)" + 
				"	else  trim(ProductSizeLabel)"
				+"end as Label\n";

		Connection conn = DriverManager.getConnection(dbUrl, dbLogin, dbPwd);
		Statement st=conn.createStatement();
		String cols = "MaterialCode, AncienCode_RCDS, SalesTextFR, SalesTextEN, MaterialDescriptionEN, MaterialDescriptionFR,ProductSizeLabel";

		String requete = "select "+cols+","+libelle+" from Product_SAP ";

		if (clientIds!=null && clientIds.size()>0) {
			String clientsSQL="(";
			for (String clientId : clientIds) {
				clientsSQL=clientsSQL+"'"+clientId+"',";
			}
			clientsSQL=clientsSQL.substring(0, clientsSQL.length()-1)+")";
			requete = requete+" where (SoldTo_CustomerCode in "+clientsSQL+" OR SoldTo_CustomerName in "+clientsSQL+" OR ShipTo_CustomerCode in "+clientsSQL+" OR ShipTo_CustomerName in "+clientsSQL				
			+" OR (SoldTo_CustomerCode is null AND SoldTo_CustomerName is null AND ShipTo_CustomerCode is null AND ShipTo_CustomerName is null) "		//Prend les produits sans clients. 		
			+") ";
		}
		else
			Commun.warn("Aucun id client fourni; Tous les produits de la base seront parsés.");

		if (Volume!=null) {
			requete = requete + (requete.toUpperCase().contains("WHERE")?" And ":"where")+" (MaterialVolumeSize is null or MaterialVolumeSize="+Volume+")";
			Commun.debug("Filtre sur le volume : "+Volume);
		}
		if (Degre!=null) {
			requete = requete + (requete.toUpperCase().contains("WHERE")?" And ":"where")+" (AlcoolRate is null or AlcoolRate="+Degre+")";
			Commun.debug("Filtre sur le Degre : "+Degre);
		}		
		if (Pack!=null) {
			requete = requete + (requete.toUpperCase().contains("WHERE")?" And ":"where")+" (MaterialNumerator is null or MaterialNumerator="+Pack+")";
			Commun.debug("Filtre sur le Pack: "+Pack);
		}


		requete = requete + " group by "+cols;

		ResultSet rs = st.executeQuery(requete);

		ResultSetMetaData rsmd = rs.getMetaData();
		int cpt = 0; 
		String code=null;
		while (rs.next()) {
			for (int i=1; i<=rsmd.getColumnCount();i++) {
				String name = rsmd.getColumnName(i);
				if (i==1) {
					code = rs.getString(name);
				}
				else if (i==2) {
					if (code == null)
						code = Commun.PREFIX_RCDS+rs.getString(name);
					else {}
				}
				else if (name.equals("Label")) {
					String lb = rs.getString(name);
					libelleProduits.put(code, lb);
					
					HashMap<Character, Double> vecteur=vecteurDunProduit.get(code);
					if (vecteur==null)
						vecteur = SkuNameParser.getInitialVector();
					if (lb!=null)
						vecteur = SkuNameParser.vectorize(lb, vecteur, false);		
					vecteurDunProduit.put(code, vecteur);

				}
				else
					ajouteProduit(code, rs.getString(name));

			}
			cpt++;	
		}
		Commun.debug(cpt+" produits trouvés dans la base.");
		return cpt; 
	}

	public int peuple(String[] args) {		
		String dbLogin=null;
		String dbUrl=null;
		String dbPwd=null;

		for (int i= 0; i <args.length;i++) {
			if (args[i].toLowerCase().trim().equals("-dblogin"))	{
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
		try {
			return peuple(dbUrl, dbLogin, dbPwd, null, null, null, null);
		}
		catch(SQLException e) {
			System.err.println("SQL a merdé");
			System.err.println(e);
		}
		return -1; 
	}

	public TreeMap<Double, HashSet<String>> matche(String libelleInconnu) {
		TreeMap<Double, HashSet<String>> sol=new TreeMap<Double, HashSet<String>>();
		HashSet<String> motsinconnus = recupereMots(libelleInconnu, null);
		double MeilleureProximiteProduit=0.0;
		String MeilleurProduit=null;
		
		for (String codeProduit : motsDunProduit.keySet()) { //Boucle surles candidats
			HashSet<String> motsConnus = motsDunProduit.get(codeProduit);
			double proximiteProduit = 0.0;
			
			
			for (String mi : motsinconnus) { //Boucle sur les mots du libellé
				String meilleurMotMache = null; 
				double meilleurScore =0;
				for (String mc : motsConnus) { //Boucle sur les mots du candidat
					double c = SkuNameParser.getLevenshteinSimilarity(mi, mc);
					if (meilleurMotMache==null) {
						meilleurMotMache = mc; 
						meilleurScore = c; 
					}
					else {
						if (meilleurScore<c) {
							meilleurMotMache = mc; 
							meilleurScore = c; 
						}
					}
				}
				proximiteProduit = proximiteProduit + meilleurScore;
			}
			proximiteProduit=proximiteProduit/(motsinconnus.size()+0.0);
			
			
			//Version cosine
			HashMap<Character, Double> vInc = SkuNameParser.vectorize(libelleInconnu, SkuNameParser.getInitialVector(), false);
			double res = cosine(vInc, vecteurDunProduit.get(codeProduit));
			proximiteProduit = (proximiteProduit*1E4)+res; 
			
			if (MeilleurProduit==null || MeilleureProximiteProduit<proximiteProduit) {
				MeilleureProximiteProduit = proximiteProduit;
				MeilleurProduit = codeProduit; 
			}
			
			HashSet<String> codes = sol.get(proximiteProduit); //récupère la liste des produits ayant déjà la même proximité
			if (codes == null)
				codes = new HashSet<String>();
			codes.add(codeProduit);
			sol.put(proximiteProduit, codes);
			
			
		}
		return sol; 
	}

	public static final String score(String fromJson, String dbUrl, String dbLogin, String dbPwd) {
		HashMap<String, Object> paires = SkuNameParser.fromJSON(fromJson);		
		//récupère le libellé à parser dans le JSON
		String libelleInconnu = (String) paires.get("ProduitCandidat");
		if (libelleInconnu == null)
			throw new NullPointerException("Aucun ProduitCandidat fourni");

		//Récupère les différents ids du customer dans le JSON, pour ne conserver que les produits achetés par ces clients
		HashSet<String> clientIds = new HashSet<String>();
		if (paires.get("CustomerCodeSAP")!=null)
			clientIds.addAll((HashSet<String>) paires.get("CustomerCodeSAP"));
		else
			Commun.warn("Aucun CustomerCodeSap fourni");
		if (paires.get("Customer")!=null)
			clientIds.add((String) paires.get("Customer"));
		else
			Commun.warn("Aucun Customer fourni");

		//if (paires.get("Customer")!=null)
		//clientIds.add((String) paires.get("Customer"));
		String jsonResult = score(libelleInconnu, clientIds, dbUrl, dbLogin, dbPwd);		
		return jsonResult; 
	}

	public static final String score(String libelleInconnu, HashSet<String> clientIds, String dbUrl, String dbLogin, String dbPwd) {
		Appareilleur p = new Appareilleur(); 
		try {
			QualifiedSKU qsku = new QualifiedSKU(libelleInconnu, null, null, null);
			p.peuple(dbUrl, dbLogin, dbPwd, clientIds, qsku.Volume, qsku.Degre, qsku.Pack);
			//p.peuple(dbUrl, dbLogin, dbPwd, clientIds, null, null, null);
		} catch (SQLException e) {
			Commun.error("Un problème lors de la requête SQL nous empêche d'obtenir les produits potentiels.");
			e.printStackTrace();
			return null; 
		}
		TreeMap<Double, HashSet<String>> sol = p.matche(libelleInconnu);
		String jsonResult = p.toJson(sol, NB_RESULTATS, SCORE_MIN);
		jsonResult = "{'Libelle':'"+libelleInconnu+"', 'candidats':"+jsonResult+"}";
		return jsonResult; 
	}

	public String toJson(TreeMap<Double, HashSet<String>> sol, int nbCandidatsMax, double matchMin) {
		String jsonResult =null;
		int nbCandidats = 0;
		for (Double d: sol.descendingKeySet()) {
			if (d<matchMin) {
				Commun.debug("Seuil atteint:"+d+" <"+matchMin);
				break;				
			}
			HashSet<String> hs = sol.get(d);
			for (String codeProduit: hs) {
				String l = listeJson(d, codeProduit);
				if (jsonResult ==null)
					jsonResult = "[";
				jsonResult = jsonResult+l+",";
			}
			nbCandidats = nbCandidats+ hs.size();
			if (nbCandidats>=nbCandidatsMax) {
				Commun.debug("Nbre de candidats atteints ("+nbCandidats+". Dernier seuil embarqué: "+d+")");
				break;
			}
		}
		if (jsonResult==null) return "[]";
		jsonResult = jsonResult.substring(0, jsonResult.length()-1)+"]";
		return jsonResult; 		
	}
	public String listeJson(Double score, String codeProduit) {
		String json = "{'Label':'"+ getMotsConcantenes(codeProduit)+"', 'Score':"+score+",";
		if (codeProduit.startsWith(Commun.PREFIX_RCDS))
			json=json+" 'IdRCDS':'"+codeProduit.substring(Commun.PREFIX_RCDS.length())+"'"; 
		else
			json=json+" 'IdSAP':'"+codeProduit+"'"; 
		json = json + "}";
		return json; 
	}

	public static void main(String[] args) {
		String dbLogin=null;
		String dbUrl=null;
		String dbPwd=null;

		for (int i= 0; i <args.length;i++) {
			if (args[i].toLowerCase().trim().equals("-dblogin"))	{
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
		String s = SkuNameParser.remplace_speciaux("200 p1000 Pipo P12 ap14f up1 p1ff 60 p20 300a");
		
		String fromJson="{\"ProduitCandidat\" : \"COUNTREAU BLOOD ORANGE 12/700ML\",\"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX-DUFRY\",\"ProductSourceCode\" : \"3035540008282\",\"CustomerCodeSAP\" : [\"0010024347\",\"0010024358\",\"0010024377\",\"0010024486\",\"0010024105\",\"0010024106\",\"0010024563\",\"0010024596\",\"0010024049\",\"0010024051\",\"0010024345\",\"0010024345\",\"0010024391\",\"0010024391\",\"0010024404\",\"0010024404\",\"0010024173\",\"0010024175\",\"0010024272\",\"0010024275\",\"0010024315\",\"0010024580\",\"0010024581\",\"0010024583\",\"0010024598\"]}";
		fromJson="{\"ProduitCandidat\" : \"REMY LOUIS XIII GIFT PACK 3X70CL\",  \"Customer\" : \"HEINEMANN-HEINEMANN-YYYYMM.XLSX\",  \"CustomerCodeSAP\" : [\"0010024584\"]}";
		fromJson="{\"ProduitCandidat\" : \"REMY LOUIS XIII GIFT PACK 3X70CL\"}";
		fromJson="{\"ProduitCandidat\" : \"COUNTREAU BLOOD ORANGE 12/700ML\", \"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX\"}";
		fromJson="{\"ProduitCandidat\" : \"METAXA 12 STAR 40 100CL\", \"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX\"}";
		fromJson="{\"ProduitCandidat\" : \"APEROL 11% loi,et,order 6X0,7\",  \"Customer\" : \"LOTTE-SG.XLSX\",  \"CustomerCodeSAP\" : [ \"0010024636\",\"0010024641\"]}";
		String jsonResult = null; 
		fromJson="{\"ProduitCandidat\" : \"SS LOUIS XIII 40 70CL\", \"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX\"}";
		fromJson="{\"ProduitCandidat\" : \"METAXA 12 STAR 40 100CL\", \"Customer\" : \"DUFRY-DUFRY-YYYYMM.XLSX\"}";
		fromJson="{\"ProduitCandidat\" : \"MOUNT GAY XO PEAT CASK 6X70CL\"}";
		fromJson="{\"ProduitCandidat\" : \"ST REMY CABERNET CASK FINISH 12X70CL\"}"; 
		fromJson="{\"ProduitCandidat\" : \"REMY MARTIN CLUB 35TH ANNIV LTD 1L 40%\"}";
		
		jsonResult = score(fromJson, dbUrl, dbLogin, dbPwd);
		Commun.prettyPrint(jsonResult);

		String[] libelles = {"R.MARTIN VSOP 40% 1000ML","REMY MARTIN VSOP 1000ML","RM VSOP 100CL","COINTREAU 100CL SHAKER","COINTREAU 1L SHAKER","CTR 1L SHAKER PACK","COINTREAU LIQUEUR SHAKER FREE 1L","LOUIS XIII TIME COLLECTION 700ML","LOUIS XIII TIME COLLECTION THE ORIGIN 70CL","LOUIS XIII TIME COLLECTION 0,7L","LOUIS XIIII TC 70CL","LOUIS XIIII TC II 70C","LOUIS XIII TIME COLLECTION 2 70CL","LOUIS XIII TIME COLLECTION 2 700ML","BOTANIST ISLAY GIN PLANTER PACK 100CLS 46","BOTANIST ISLAY GIN 46% 1L PLANTER","THE BOTANIST GIN PLANTER PCK 100CL 46% ","THE BOTANIST ISLAY DRY GIN 1L PLANTER","RM XO CANNES EDITION 0.7L 40% GP","REMY M XO CANNES EDITION 0.7L 40% GP","REMY MARTIN XO CANNES 700ML","REMY MARTIN XO CANNES 2020 0,7L","REMY MARTIN XO CNS 70CL 40°","REMY MARTIN XO CNY 40% 700ML","RÉMY MARTIN XO CNY 2020 40% 0.7L","REMY MARTIN XO CHINESE NEW YEAR 70CL","REMY XO CNY 2019 LE 40% 70CL","R MARTIN XO STEAVEN RICHARD 40% 700ML","REMY MARTIN XO STEAVEN RICHARD 40% 70CL","RM STEAVEN RICHARD XO 70CL 40%","RM S.RICHARD XO 70CL 40%"};
		//String[] libelles = {"BOTANIST ISLAY GIN PLANTER PACK 100CLS 46","BOTANIST ISLAY GIN 46% 1L PLANTER","THE BOTANIST GIN PLANTER PCK 100CL 46% ","THE BOTANIST ISLAY DRY GIN 1L PLANTER"};
		HashMap<String, String> idsTrouves = new HashMap<String, String>(); 
		for (String libelleInconnu: libelles) {
			jsonResult = score(libelleInconnu, null, dbUrl, dbLogin, dbPwd);
			Commun.prettyPrint(jsonResult);
			//String idSap = Commun.getFirstCodeFromJsonResult(jsonResult);
			//idsTrouves.put(libelleInconnu, idSap);
		}
		Commun.log(idsTrouves.toString());
	}
}