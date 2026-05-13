package dev.i726.rocky.module;

import java.util.ArrayList;
import java.util.List;

public class Category {
    private String name;
    private boolean editable;
    private Category parent;
    private List<Category> subcategories = new ArrayList<>();
    
    public Category(String name) {
        this.name = name;
        this.editable = true;
    }
    
    public Category(String name, boolean editable) {
        this.name = name;
        this.editable = editable;
    }
    
    public Category(String name, Category parent) {
        this.name = name;
        this.editable = true;
        this.parent = parent;
        if (parent != null) parent.addSubcategory(this);
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        if (editable) {
            this.name = name;
        }
    }
    
    public boolean isEditable() {
        return editable;
    }
    
    public Category getParent() {
        return parent;
    }
    
    public List<Category> getSubcategories() {
        return subcategories;
    }
    
    public void addSubcategory(Category sub) {
        if (!subcategories.contains(sub)) {
            subcategories.add(sub);
        }
    }
    
    public boolean isSubcategory() {
        return parent != null;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Category category = (Category) obj;
        return name.equals(category.name);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public String toString() {
        return name;
    }
}
