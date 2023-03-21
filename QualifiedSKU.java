package com.viseo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QualifiedSKU {
	String Label; 
	Double Pack; 
	Double Degre; 
	Double Volume; 
	String Remains; 
	
	/**
	 * 
	 * @param label Libellé initial à stocker
	 * @param av Liste des volumes autorisés
	 * @param as Liste des strength autorisés
	 * @param ap Liste des packs autorisés
	 */
	public QualifiedSKU(String label, TreeSet<Double> av, TreeSet<Double> as, TreeSet<Double> ap) {
		Label =replaceDigitSeparator(label); 
		parse(av, as, ap);
	}
	
	static HashMap getVolume(String label, TreeSet<Double> allowed) {		
		LinkedHashMap<String, Double> u = new LinkedHashMap();
		u.put("L", 1.0);
		u.put("DL", 10.0);
		u.put("CL", 100.0);
		u.put("ML", 1000.0);
		
		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValueIn(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValue(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValueSpaced(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		return null; 
	}
	
	static HashMap getStrength(String label, TreeSet<Double> allowed) {		
		LinkedHashMap<String, Double> u = new LinkedHashMap();
		u.put("%", 1.0);

		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValueIn(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValue(label, k, u.get(k));
			if (d.get("value")!=null  && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValueSpaced(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		return null; 
	}

	static HashMap getPack(String label, TreeSet<Double> allowed) {		
		LinkedHashMap<String, Double> u = new LinkedHashMap();
		u.put("X", 1.0);

		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValue(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}

		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValueAfter(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		for (String k : u.keySet()) {
			HashMap<String, Object> d = getUnitValueSpaced(label, k, u.get(k));
			if (d.get("value")!=null && (allowed==null || allowed.contains((Double) d.get("value"))))
					return d;
		}
		return null; 
	}

	static HashMap<String, Object> getUnitValue(String label, String unit, Double ratio) {
		String r = "(\\d)+(\\.(\\d)+)?"+unit;
		Pattern p = Pattern.compile(r);
		Matcher m = p.matcher(label);
		HashMap<String, Object> sol = new HashMap<String, Object>();
		sol.put("remains", label); 
		if (m.find()) {
			try {
				Double v= Double.parseDouble(label.substring(m.start(), m.end()).replace(unit,""));
				String remains =label.substring(0,m.start())+" "+label.substring(m.end()).replaceAll("  "," ").trim();
				sol.put("value", v/ratio);
				sol.put("remains", remains);
			}catch(NumberFormatException e) {
				
			}
			
		}
		return sol;
	}
	/**
	 * Filtre les individus de la liste l sur les 3 critères: Volume et Degre et Pack de notre instance
	 * Le critère est utilisé pour filtrer si il n'est pas null. 
	 * Idem : Tout indidivu de la liste dont le critère est null est automatiquement conservé. 
	 * @param l
	 * @return
	 * @throws Exception
	 */
	LinkedHashSet<HashMap<String, Object>> filtre(LinkedHashSet<HashMap<String, Object>> l) throws Exception{
		LinkedHashSet<HashMap<String, Object>> l1;
		if (l.size()==0) {
			Commun.warn("List of products is already empty. No need to filter");
			return l; 
		}
		if (true) return l; 
		
		Commun.log("Filtre appliqué : Volume = "+Volume+", Strength = "+Degre+", Pack = "+Pack);
		int ll = l.size(); 	
		if (Volume!=null) {
			l1 = SkuNameParser.filtre(l, "Volume", Volume);
			if (l1.size()==0){
					Commun.log(" All products filtered out because of volume "+Volume+". Must be wrong. We do not apply this filter.");
					l1 = l;
			}
			else
			Commun.log(l1.size()+"/"+ll+"=>"+(ll-l1.size()) +" products filtered out because their volume is not "+Volume);
		}
		else
			l1 =l; 
		
		ll = l1.size(); 
		if (Degre!=null){
			l1 = SkuNameParser.filtre(l1, "Strength", Degre);
			Commun.log(l1.size()+"/"+ll+"=>"+(ll-l1.size()) +" products filtered out because their Strength is not "+Degre);
		}
		ll = l1.size(); 
		LinkedHashSet<HashMap<String, Object>> l2;

		if (Pack!=null) {
			l2 = SkuNameParser.filtre(l1, "Pack", Pack);
			Commun.log(l2.size()+"/"+ll+"=>"+(ll-l2.size()) +" products filtered out because their Pack size is not "+Pack);
		}
		else
			return l1; 
		
		if (l2.size()==0) {
			Commun.warn("Filter on pack is emptying the remaining list of products. We do not apply the last filter.");
			return l1; 
		}
		return l2; 
	}
	
	static HashMap<String, Object> getUnitValueAfter(String label, String unit, Double ratio) {
		String r = unit+"(\\d)+";
		Pattern p = Pattern.compile(r);
		Matcher m = p.matcher(label);
		HashMap<String, Object> sol = new HashMap<String, Object>();
		sol.put("remains", label); 
		if (m.find()) {
			try {
				Double v= Double.parseDouble(label.substring(m.start(), m.end()).replace(unit,""));
				String remains =label.substring(0,m.start())+" "+label.substring(m.end()).replaceAll("  "," ").trim();
				sol.put("value", v/ratio);
				sol.put("remains", remains);
			}catch(NumberFormatException e) {
				
			}
			
		}
		return sol;
	}
	static HashMap<String, Object> getUnitValueSpaced(String label, String unit, Double ratio) {
		String r = "(\\d)+(\\.(\\d)+)?( )+"+unit;
		Pattern p = Pattern.compile(r);
		Matcher m = p.matcher(label);
		HashMap<String, Object> sol = new HashMap<String, Object>();
		sol.put("remains", label); 
		if (m.find()) {
			try {
				Double v= Double.parseDouble(label.substring(m.start(), m.end()).replace(unit,""));
				String remains =(label.substring(0,m.start())+" "+label.substring(m.end()).replaceAll("  "," ")).trim();
				sol.put("value", v/ratio);
				sol.put("remains", remains);
			}catch(NumberFormatException e) {
				
			}
			
		}
		return sol;
	}

	static HashMap<String, Object> getUnitValueIn(String label, String unit, Double ratio) {
		String r = "(\\d)+"+unit+"(\\d)+";
		Pattern p = Pattern.compile(r);
		Matcher m = p.matcher(label);
		HashMap<String, Object> sol = new HashMap<String, Object>();
		sol.put("remains", label); 
		if (m.find()) {
			try {
				Double v= Double.parseDouble(label.substring(m.start(), m.end()).replace(unit,"."));
				String remains =label.substring(0,m.start())+" "+label.substring(m.end()).replaceAll("  "," ").trim();
				sol.put("value", v/ratio);
				sol.put("remains", remains);
			}catch(NumberFormatException e) {
				
			}
			
		}
		return sol;
	}

	/**
	 * Recherche dans {@link #remains} le volume, le pack et le degré d'alcool.Une fois la portion de texte associée à l'une de ces valeurs identifiée, elle est retirée du label, et la résultante est stockée {@link #Remains}. 
	 */
	private void parse(TreeSet<Double> av, TreeSet<Double> as, TreeSet<Double> ap) {
		Remains = Label; 
		
		HashMap d2=getVolume(Label, av);
		Volume = d2!=null?(Double) d2.get("value"):null;
		Remains = d2!=null?(String) d2.get("remains"):Remains;

		d2=getStrength(Remains, as);
		Degre = d2!=null?(Double) d2.get("value"):null;
		Remains = d2!=null?(String) d2.get("remains"):Remains;

		d2=getPack(Remains, ap);
		Pack = d2!=null?(Double) d2.get("value"):null;
		Remains = d2!=null?(String) d2.get("remains"):Remains;
	
		Remains = Remains.trim();
		while (Remains.contains("  "))
			Remains = Remains.replaceAll("  ", " ");
		
	}
	
	private String replaceDigitSeparator(String label2) {
		if (label2==null) return label2;
		if (!label2.contains(",")) return label2;
		Pattern p = Pattern.compile("(\\d)+(,)(\\d)+");
		Matcher m = p.matcher(label2);
		while (m.find()) {
			System.out.println(m.group(2));
			label2 = label2.substring(0,m.start(2))+"."+label2.substring(m.start(2)+1);
		}
		return label2;
	}

	public String toCSV(String sep) {
		return Label + sep+ Volume+sep+Degre+sep+Pack+sep+Remains;		
	}
	
	public String toString() {
		return Label + "\t\tL="+Volume+"\tD="+Degre+"\tP="+Pack+"\t remains='"+Remains+"'";
		
	}
}
