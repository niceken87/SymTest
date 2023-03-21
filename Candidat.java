package com.viseo;

import java.util.Objects;

public class Candidat {
	String libelle; 
	String id;
	
	public Candidat(String libelle_, String id_) {
		id = id_;
		libelle = libelle_;
	}
	
	public String getLibelle() {
		return libelle; 
	}
	
	public String getId() {
		return id; 
	}
	
	@Override
    public boolean equals(Object o) {

        if (o == this) return true;
        if (!(o instanceof Candidat)) {
            return false;
        }
        return getLibelle().equals(((Candidat) o).getLibelle());
     }

    @Override
    public int hashCode() {
        return Objects.hash(libelle);
    }

    @Override
    public String toString() {
    	return libelle+"("+id+")";
    }
}
