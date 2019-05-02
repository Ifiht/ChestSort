package de.jeffclan.JeffChestSort;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class JeffChestSortOrganizer {
	
	// This is the heart of ChestSort!
	// All of the sorting stuff happens here.

	/*
	 * Thoughts before implementing:
	 * We create a string from each item that can be sorted.
	 * We will omit certain parts of the name and put them behind the main name for sorting reasons.
	 * E.g. ACACIA_LOG -> LOG_ACACIA (so all LOGs are grouped)
	 * Diamond, Gold, Iron, Stone, Wood does NOT have to be sorted, because they are already alphabetically in the right order
	 */

	JeffChestSortPlugin plugin;

	// All available colors in the game. We will strip this from the item names and keep the color in a separate variable
	static final String[] colors = { "white", "orange", "magenta", "light_blue", "light_gray", "yellow", "lime", "pink", "gray",
			"cyan", "purple", "blue", "brown", "green", "red", "black" };
	
	// The same applies for wood. We strip the wood name from the item name and keep it in the above mentioned color variable
	static final String[] woodNames = { "acacia", "birch", "jungle", "oak", "spruce", "dark_oak" };

	// We store a list of all Category objects
	ArrayList<JeffChestSortCategory> categories = new ArrayList<JeffChestSortCategory>();

	JeffChestSortOrganizer(JeffChestSortPlugin plugin) {
		this.plugin = plugin;

		// Load Categories
		File categoriesFolder = new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "categories" + File.separator);
		File[] listOfCategoryFiles = categoriesFolder.listFiles();
		for (File file : listOfCategoryFiles) {
			if (file.isFile()) {
				// Category name is the filename without .txt
				String categoryName = file.getName().replaceFirst(".txt", "");
				try {
					categories.add(new JeffChestSortCategory(categoryName,getArrayFromCategoryFile(file)));
					if(plugin.verbose) {
						plugin.getLogger().info("Loaded category file "+file.getName());
					}
				} catch (FileNotFoundException e) {
					plugin.getLogger().warning("Could not load category file: "+file.getName());
					e.printStackTrace();
				}
			}
		}

	}

	// Returns an array with all typematches listed in the category file
	String[] getArrayFromCategoryFile(File file) throws FileNotFoundException {
		Scanner sc = new Scanner(file);
		List<String> lines = new ArrayList<String>();
		while (sc.hasNextLine()) {
			//if(!sc.nextLine().startsWith("#")) {
		  lines.add(sc.nextLine());
			//}
		}
		String[] arr = lines.toArray(new String[0]);
		sc.close();
		return arr;
	}


	// Convert the item name to what I call a "sortable item name".
	// Sorry, the method name is a bit misleading.
	// The array's [0] value contains the item name with a few fixes, see below
	// The array's [1] value contains the color or wood name of the item, or "<none>"
	String[] getTypeAndColor(String typeName) {

		// [0] = Sortable Item name
		// [1] = Color/Wood

		String myColor = "<none>";
		
		// Only work with lowercase
		typeName = typeName.toLowerCase();

		// When a color occurs at the beginning (e.g. "white_wool"), we omit the color so that the color will not
		// determine the beginning letters of the sortable item name
		for (String color : colors) {
			if (typeName.startsWith(color)) {
				typeName = typeName.replaceFirst(color + "_", "");
				myColor = color;
			}
		}
		// Same for wood, but the wood name can also be in the middle of the item name, e.g. "stripped_oak_log"
		for(String woodName : woodNames) {
			if(typeName.equals(woodName+"_wood")) {
				typeName = "log_wood";
				myColor = woodName;
			}
			else if(typeName.startsWith(woodName)) {
				typeName = typeName.replaceFirst(woodName+"_", "");
				myColor = woodName;
			}
			else if(typeName.equals("stripped_"+woodName+"_log")) {
				//typeName = typeName.replaceFirst("stripped_"+woodName+"_", "stripped_");
				typeName = "log_stripped";
				myColor = woodName;
			} else if(typeName.equals("stripped_"+woodName+"_wood")) {
				typeName = "log_wood_stripped";
				myColor = woodName;
			}
		}

		// "egg" has to be put in front to group spawn eggs
		// e.g. cow_spawn_egg -> egg_cow_spawn
		if(typeName.endsWith("_egg")) {
			typeName = typeName.replaceFirst("_egg", "");
			typeName = "egg_" + typeName;
		}

		// polished_andesite -> andesite_polished
		if(typeName.startsWith("polished_")) {
			typeName = typeName.replaceFirst("polished_", "");
			typeName = typeName + "_polished";
		}

		// Group wet and dry sponges
		if(typeName.equalsIgnoreCase("wet_sponge")) {
			typeName = "sponge_wet";
		}

		// Group pumpkins and jack-o-lanterns / carved pumpkins
		if(typeName.equalsIgnoreCase("carved_pumpkin")) {
			typeName = "pumpkin_carved";
		}

		// Sort armor: helmet, chestplate, leggings, boots
		// We add a number to keep the armor in the "right" order
		if(typeName.endsWith("helmet")) {
			typeName = typeName.replaceFirst("helmet", "1_helmet");
		} else if(typeName.endsWith("chestplate")) {
			typeName = typeName.replaceFirst("chestplate", "2_chestplate");
		} else if(typeName.endsWith("leggings")) {
			typeName = typeName.replaceFirst("leggings", "3_leggings");
		} else if(typeName.endsWith("boots")) {
			typeName = typeName.replaceFirst("boots", "4_boots");
		}

		// Group horse armor
		if(typeName.endsWith("horse_armor")) {
			typeName = typeName.replaceFirst("_horse_armor", "");
			typeName = "horse_armor_" + typeName;
		}

		String[] typeAndColor = new String[2];
		typeAndColor[0] = typeName;
		typeAndColor[1] = myColor;

		return typeAndColor;
	}

	// This method takes a sortable item name and checks all categories for a match
	// If none, matches, return "<none>" (it will be put behind all categorized items when sorting by category)
	String getCategory(String typeName) {
		typeName = typeName.toLowerCase();
		for (JeffChestSortCategory cat : categories) {
			if (cat.matches(typeName)) {
				return cat.name;
			}
		}
		return "<none>";
	}

	// This puts together the sortable item name, the category, the color, and whether the item is a block or a "regular item"
	String getSortableString(ItemStack item) {
		char blocksFirst;
		char itemsFirst;
		if (item.getType().isBlock()) {
			blocksFirst = '!'; // ! is before # in ASCII
			itemsFirst = '#';
		} else {
			blocksFirst = '#';
			itemsFirst = '!';
		}

		String[] typeAndColor = getTypeAndColor(item.getType().name());
		String typeName = typeAndColor[0];
		String color = typeAndColor[1];
		String category = getCategory(item.getType().name());

		// The hashcode actually not needed anymore, but I kept it for debugging purposes
		String hashCode = String.valueOf(getBetterHash(item));

		// Generate the strings that finally are used for sorting.
		// They are generated according to the config.yml's sorting-method option
		String sortableString = plugin.sortingMethod.replaceAll("\\{itemsFirst\\}", String.valueOf(itemsFirst));
		sortableString = sortableString.replaceAll("\\{blocksFirst\\}", String.valueOf(blocksFirst));
		sortableString = sortableString.replaceAll("\\{name\\}", typeName);
		sortableString = sortableString.replaceAll("\\{color\\}", color);
		sortableString = sortableString.replaceAll("\\{category\\}", category);
		sortableString = sortableString + "," + hashCode;

		return sortableString;

	}

	// Sort a complete inventory
	void sortInventory(Inventory inv) {
		sortInventory(inv,0,inv.getSize()-1);
	}

	// Sort an inventory only between startSlot and endSlot
	void sortInventory(Inventory inv,int startSlot, int endSlot) {

		// This has been optimized as of ChestSort 3.2.
		// The hashCode is just kept for legacy reasons, it is actually not needed.

		if(plugin.debug) {
			System.out.println(" ");
			System.out.println(" ");
		}


		// We copy the complete inventory into an array
		ItemStack[] items = inv.getContents();

		// Get rid of all stuff before startSlot...
		for(int i = 0; i<startSlot;i++) {
			items[i] = null;
		}
		// ... and after endSlot
		for(int i=endSlot+1;i<inv.getSize();i++) {
			items[i] = null;
		}

		// Remove the stuff from the original inventory
		for(int i = startSlot; i<=endSlot;i++) {
			inv.clear(i);
		}

		// We don't want to have stacks of null, so we create a new ArrayList and put in everything != null
		ArrayList<ItemStack> nonNullItemsList = new ArrayList<ItemStack>();
		for(ItemStack item : items) {
			if(item!=null) {
				nonNullItemsList.add(item);
			}
		}

		// We no longer need the original array that includes all the null-stacks
		items=null;

		// We need the new list as array. So why did'nt we take an array from the beginning?
		// Because I did not bother to count the number of non-null items beforehand.
		// TODO: Feel free to make a Pull request if you want to save your server a few nanoseconds :)
		ItemStack[] nonNullItems = nonNullItemsList.toArray(new ItemStack[nonNullItemsList.size()]);

		// Sort the array with ItemStacks according to each ItemStacks' sortable String
		Arrays.sort(nonNullItems,new Comparator<ItemStack>(){
            @Override
			public int compare(ItemStack s1,ItemStack s2){
                  return(getSortableString(s1).compareTo(getSortableString(s2)));
            }});

		// Now, we put everything back in a temporary inventory to combine ItemStacks even when using strict slot sorting
		// Thanks to SnackMix for this idea!
		// Without doing this, it would not be possible to sort an inventory with a startSlot other than 0,
		// because Spigot's add(ItemStack...) method will always to store the ItemStack in the first possible slot
		
		// Create the temporary inventory with a null holder. 54 slots is enough for every inventory
		Inventory tempInventory = Bukkit.createInventory(null, 54); //cannot be bigger than 54 as of 1.14

		for(ItemStack item : nonNullItems) {
			if(plugin.debug) System.out.println(getSortableString(item));
			// Add the item to the temporary inventory
			tempInventory.addItem(item);
		}

		// Now, we iterate through all slots between startSlot and endSlot in the original inventory
		// and set those to whatever the temporary inventory contains
		// Since we already deleted all those slots, there is no chance for item duplication
		int currentSlot = startSlot;
		for(ItemStack item : tempInventory.getContents()) {
			// Ignore null ItemStacks. TODO: Actually, we could skip the for-loop here because
			// our temporary inventory was already sorted. Feel free to make a pull request to
			// save your server half a nanosecond :)
			if(item==null) continue; 
			inv.setItem(currentSlot, item);
			currentSlot++;
		}
	}
	
	// I wanted to fix the skull problems here. Instead, I ended up not using the hashCode at all.
	// I still left this here for nostalgic reasons. Also it is nice to see the hashcodes when debug is enabled
	// TODO: feel free to remove this and all references, it is really not needed anymore.
	private static int getBetterHash(ItemStack item) {
		// I used to add some metadata here, but it has been removed since a long time
		// as I said: feel free to remove this completely
		return item.hashCode();
	}

}
