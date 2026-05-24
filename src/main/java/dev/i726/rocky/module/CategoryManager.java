package dev.i726.rocky.module;

import dev.i726.rocky.utils.EncryptedString;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import net.minecraft.client.MinecraftClient;

public class CategoryManager {
    private static final Map<String, Category> categories = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> categoryModules = new ConcurrentHashMap<>();
    private static final File CATEGORIES_FILE = new File("hydrogen/categories.txt");
    
    // Main categories
    public static final Category COMBAT = new Category("Combat");
    public static final Category PLAYER = new Category("Player");
    public static final Category VISUAL = new Category("Visual");
    public static final Category MISC = new Category("Misc");
    
    // Combat subcategories
    public static final Category PVP = new Category("PvP", COMBAT);
    public static final Category CRYSTAL = new Category("Crystal", COMBAT);
    public static final Category INVENTORY = new Category("Inventory", COMBAT);
    
    // Player subcategories
    public static final Category MOVEMENT = new Category("Movement", PLAYER);
    public static final Category BRIDGING = new Category("Bridging", PLAYER);
    public static final Category AUTOMATION = new Category("Automation", PLAYER);
    
    // Visual subcategories
    public static final Category ESP = new Category("ESP", VISUAL);
    
    // Misc subcategories
    public static final Category NETWORK = new Category("Network", MISC);
    public static final Category GUI = new Category("GUI", MISC);

    // Blatant category — only works on servers without anti-cheat
    public static final Category BLATANT = new Category("Blatant", false);
    
    static {
        registerCategory(COMBAT);
        registerCategory(PLAYER);
        registerCategory(VISUAL);
        registerCategory(MISC);
        registerCategory(PVP);
        registerCategory(CRYSTAL);
        registerCategory(INVENTORY);
        registerCategory(MOVEMENT);
        registerCategory(BRIDGING);
        registerCategory(AUTOMATION);
        registerCategory(ESP);
        registerCategory(NETWORK);
        registerCategory(GUI);
        registerCategory(BLATANT);
        loadCategories();
    }
    
    public static void registerCategory(Category category) {
        categories.put(category.getName(), category);
        categoryModules.putIfAbsent(category.getName(), new ArrayList<>());
    }
    
    public static Category createCategory(String name) {
        Category category = new Category(name);
        registerCategory(category);
        saveCategories();
        return category;
    }
    
    public static void removeCategory(String name) {
        if (!isDefaultCategory(name)) {
            categories.remove(name);
            categoryModules.remove(name);
            saveCategories();
        }
    }
    
    public static void deleteCategory(Category category) {
        removeCategory(category.getName());
    }
    
    public static void renameCategory(String oldName, String newName) {
        if (!isDefaultCategory(oldName)) {
            Category category = categories.remove(oldName);
            if (category != null) {
                category.setName(newName);
                categories.put(newName, category);
                List<String> modules = categoryModules.remove(oldName);
                categoryModules.put(newName, modules);
                saveCategories();
            }
        }
    }
    
    public static void moveModule(String moduleName, String fromCategory, String toCategory) {
        List<String> from = categoryModules.get(fromCategory);
        List<String> to = categoryModules.get(toCategory);
        if (from != null && to != null) {
            from.remove(moduleName);
            to.add(moduleName);
        }
    }
    
    public static Collection<Category> getCategories() {
        return categories.values();
    }
    
    public static Category getCategory(String name) {
        return categories.get(name);
    }
    
    private static boolean isDefaultCategory(String name) {
        return name.equals("Combat") || name.equals("Player") || name.equals("Visual") || 
               name.equals("Misc") || name.equals("PvP") || name.equals("Crystal") || 
               name.equals("Inventory") || name.equals("Movement") || name.equals("Bridging") ||
               name.equals("Automation") || name.equals("ESP") || name.equals("Network") || 
               name.equals("GUI") || name.equals("Blatant");
    }
    
    private static void saveCategories() {
        try {
            CATEGORIES_FILE.getParentFile().mkdirs();
            try (PrintWriter writer = new PrintWriter(new FileWriter(CATEGORIES_FILE))) {
                for (Category category : categories.values()) {
                    if (!isDefaultCategory(category.getName())) {
                        writer.println(category.getName());
                    }
                }
            }
        } catch (IOException e) {
            // Ignore save errors
        }
    }
    
    public static void saveModuleCategories() {
        // This will be called by ModuleManager when saving modules
        saveCategories();
    }
    
    private static void loadCategories() {
        if (!CATEGORIES_FILE.exists()) return;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(CATEGORIES_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !categories.containsKey(line)) {
                    registerCategory(new Category(line));
                }
            }
        } catch (IOException e) {
            // Ignore load errors
        }
    }
}
