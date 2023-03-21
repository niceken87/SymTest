package com.viseo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Commun {

	public static void warn(String s) {
		System.out.println("WRN\t"+s);
	}

	public static void error(String s) {
		System.err.println("ERR\t"+s);
	}

	public static void log(String s) {
		System.out.println("LOG\t"+s);
	}

	public static void debug(String s) {
		System.out.println("DBG\t"+s);
	}

	final static public String PREFIX_RCDS="_RCDS_";

	final static String getFirstCodeFromJsonResult(String jsonResult) {
		String lib = ""; 
		String idSap = "";
		String chap = "'IdSAP':";
		int a = jsonResult.indexOf(chap);
		if (a>0) {
			String ss = jsonResult.substring(a+chap.length());
			idSap = ss.substring(0, ss.indexOf(","));
		}
		
		chap = "'Libelle':";
		a = jsonResult.indexOf(chap);
		if (a>0) {
			String ss = jsonResult.substring(a+chap.length());
			lib = ss.substring(0, ss.indexOf(","));
		}
		Commun.log(lib+"->"+idSap);
		return idSap; 
	}

	public static HashMap<String, String> parseFinalJson2(String json) {
		HashMap<String, String> sol = new HashMap<String, String>();

		Pattern p = Pattern.compile("\\\"Label\\\":([^\\}^,]+)[,\\}]");
		Matcher m = p.matcher(json);
		while (m.find()) {
			sol.put("Label", m.group(1));
		}
		p = Pattern.compile("\\\"Score\\\":([^\\}^,]+)[,\\}]");
		m = p.matcher(json);
		while (m.find()) {
			sol.put("Score", m.group(1));
		}
		p = Pattern.compile("\\\"IdSAP\\\":([^\\}^,]+)[,\\}]");
		m = p.matcher(json);
		while (m.find()) {
			sol.put("IdSAP", m.group(1));
		}
		p = Pattern.compile("\\\"IdRCDS\\\":([^\\}^,]+)[,\\}]");
		m = p.matcher(json);
		while (m.find()) {
			sol.put("IdRCDS", m.group(1));
		}		
		return sol; 
	}
	public static HashMap<String, Object> parseFinalJson(String json_) {
		if (json_==null || json_.length()<=2) return null;
		String json = json_.replaceAll("'", "\"");
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
			Pattern p2 = Pattern.compile("\\{[^\\{]*\\}");
			Matcher m2 = p2.matcher(m.group(2));
			List<HashMap<String, String>> liste = new ArrayList<HashMap<String, String>>();
			while (m2.find()) {
				liste.add(parseFinalJson2(m2.group(0)));
			}
			sol.put(m.group(1), liste);			
		}
		return sol;
	}
	
	public final static void prettyPrint(String jsonResult) {
		HashMap<String, Object> a = parseFinalJson(jsonResult);
		Commun.log("\t"+a.get("Libelle").toString());
		List<HashMap<String, String>> l = (List<HashMap<String, String>>) a.get("candidats");
		if (l!=null)
			for (HashMap<String, String> elt: l) {
				String r = "\t\t"+elt.get("Label")+"\t"+elt.get("Score")+"\t"+elt.get("IdSAP")+"\t"+elt.get("IdRCDS");
				Commun.log(r);
			}
	}
}
