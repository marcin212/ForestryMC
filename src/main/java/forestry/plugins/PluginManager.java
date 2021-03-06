/*******************************************************************************
 * Copyright (c) 2011-2014 SirSengir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 * 
 * Various Contributors including, but not limited to:
 * SirSengir (original work), CovertJaguar, Player, Binnie, MysteriousAges
 ******************************************************************************/
package forestry.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cpw.mods.fml.common.IFuelHandler;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.registry.GameRegistry;

import forestry.api.core.IPlugin;
import forestry.core.ForestryCore;
import forestry.core.interfaces.IOreDictionaryHandler;
import forestry.core.interfaces.IPacketHandler;
import forestry.core.interfaces.IPickupHandler;
import forestry.core.interfaces.IResupplyHandler;
import forestry.core.interfaces.ISaveEventHandler;
import forestry.core.proxy.Proxies;

public class PluginManager {

	/**
	 * You can use the API from your own mod or write a simple plugin mod using
	 * this plugin interface. Adding a plugin here will have Forestry call the
	 * registered object during ModsLoaded(). See {@link IPlugin} for further
	 * details.
	 */
	public static ArrayList<IPlugin> plugins = new ArrayList<IPlugin>();
	public static ArrayList<IGuiHandler> guiHandlers = new ArrayList<IGuiHandler>();
	public static ArrayList<IPacketHandler> packetHandlers = new ArrayList<IPacketHandler>();
	public static ArrayList<IPickupHandler> pickupHandlers = new ArrayList<IPickupHandler>();
	public static ArrayList<ISaveEventHandler> saveEventHandlers = new ArrayList<ISaveEventHandler>();
	public static ArrayList<IResupplyHandler> resupplyHandlers = new ArrayList<IResupplyHandler>();
	public static ArrayList<IOreDictionaryHandler> dictionaryHandlers = new ArrayList<IOreDictionaryHandler>();

	public static void loadPlugins(File modLocation) {
		loadIncludedPlugins(modLocation);
		loadExternalPlugins(modLocation);
	}

	private static void loadIncludedPlugins(File modLocation) {

		ClassLoader classLoader = ForestryCore.class.getClassLoader();

		// Internal plugin when Forestry is a jar or zip
		if (modLocation.isFile() && (modLocation.getName().endsWith(".jar") || modLocation.getName().endsWith(".zip")))
			loadPluginsFromFile(modLocation, classLoader);
		else if (modLocation.isDirectory())
			loadPluginsFromBin(modLocation, classLoader);

	}

	private static void loadExternalPlugins(File modLocation) {

		try {

			File pluginDir = new File(Proxies.common.getForestryRoot() + "/mods");
			ClassLoader classLoader = ForestryCore.class.getClassLoader();

			// Abort if the plugin directory is not there.
			if (!pluginDir.isDirectory())
				return;

			File[] fileList = pluginDir.listFiles();
			if (fileList == null)
				return;

			for (File file : fileList) {

				if (!file.isFile())
					continue;

				if (!file.getName().endsWith(".jar") && !file.getName().endsWith(".zip"))
					continue;

				if (file.getName().equals(modLocation.getName()))
					continue;

				loadPluginsFromFile(file, classLoader);

			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private static void loadPluginsFromFile(File file, ClassLoader classLoader) {

		String pluginName;

		try {
			ZipEntry entry = null;
			FileInputStream fileIO = new FileInputStream(file);
			ZipInputStream zipIO = new ZipInputStream(fileIO);

			while (true) {
				entry = zipIO.getNextEntry();

				if (entry == null) {
					fileIO.close();
					break;
				}

				String entryName = entry.getName();
				File entryFile = new File(entryName);
				pluginName = entryFile.getName();
				if (!entry.isDirectory() && pluginName.startsWith("Plugin") && pluginName.endsWith(".class"))
					PluginManager.addPlugin(classLoader, pluginName, entryFile.getPath().replace(File.separatorChar, '.'));

			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

	}

	private static String parseClassName(String binpath) {

		String[] tokens = binpath.split("[\\\\/]");
		String packageName = "";
        boolean inIdea = false;

		for (int i = 0; i < tokens.length; i++) {
			if (tokens[i] == null)
				break;
            if (tokens[i].equals("production") || tokens[i].equals("classes"))
                inIdea = true;
			if (!tokens[i].equals("bin") && !inIdea)
				continue;

			// We are at bin, build the rest and return
			for (int j = (inIdea ? i + 2 : i + 1); j < tokens.length; j++) {
				if (packageName.length() > 0)
					packageName += ".";
				packageName += tokens[j];
			}
			break;
		}

		return packageName;
	}

	private static void loadPluginsFromBin(File bin, ClassLoader classLoader) {
		File[] fileList = bin.listFiles();

		// ensure consistent sorting across all platforms
		Arrays.sort(fileList, new Comparator<File>() {
			@Override
			public int compare(File a, File b) {
				return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
			}
		});

		for (File file : fileList) {
			String pluginName = file.getName();

			if (file.isFile() && pluginName.startsWith("Plugin") && pluginName.endsWith(".class")) {
				PluginManager.addPlugin(classLoader, pluginName, parseClassName(file.getPath()));
			} else if (file.isDirectory()) {
				loadPluginsFromBin(file, classLoader);
			}
		}
	}

	public static void addPlugin(ClassLoader classLoader, String pluginName, String packageName) {

		if (pluginName.equals("PluginManager.class") || pluginName.equals("PluginInfo.class"))
			return;

		String pluginClassName = packageName.replace(".class", "").replace("minecraft.", "");

		try {

			Class<?> pluginClass = null;
			try {
				pluginClass = classLoader.loadClass(pluginClassName);
			} catch (Throwable error) {
			}

			if (pluginClass == null) {
				pluginClass = Class.forName(pluginClassName);
			}

			if (pluginClass != null) {

				Class<?> clz = pluginClass;
				boolean isPlugin = false;
				do {
					for (Class<?> i : clz.getInterfaces()) {
						if (i == IPlugin.class) {
							isPlugin = true;
							break;
						}
					}

					clz = clz.getSuperclass();
				} while (clz != null && !isPlugin);

				if (!isPlugin)
					return;

				IPlugin plugin = (IPlugin) pluginClass.newInstance();
				if (plugin != null) {

					Proxies.log.fine("Found plugin " + plugin.toString());
					plugins.add(plugin);

					if (plugin instanceof NativePlugin) {

						NativePlugin nplugin = (NativePlugin) plugin;
						IGuiHandler guiHandler = nplugin.getGuiHandler();
						if (guiHandler != null)
							guiHandlers.add(guiHandler);

						IPacketHandler packetHandler = nplugin.getPacketHandler();
						if (packetHandler != null)
							packetHandlers.add(packetHandler);

						IPickupHandler pickupHandler = nplugin.getPickupHandler();
						if (pickupHandler != null)
							pickupHandlers.add(pickupHandler);

						ISaveEventHandler saveHandler = nplugin.getSaveEventHandler();
						if (saveHandler != null)
							saveEventHandlers.add(saveHandler);

						IResupplyHandler resupplyHandler = nplugin.getResupplyHandler();
						if (resupplyHandler != null)
							resupplyHandlers.add(resupplyHandler);

						IOreDictionaryHandler dictionaryHandler = nplugin.getDictionaryHandler();
						if (dictionaryHandler != null)
							dictionaryHandlers.add(dictionaryHandler);
					}

					if (plugin instanceof IFuelHandler)
						GameRegistry.registerFuelHandler((IFuelHandler) plugin);

				}
			}

		} catch (Throwable ex) {
			//			ex.printStackTrace();
		}
	}
}
