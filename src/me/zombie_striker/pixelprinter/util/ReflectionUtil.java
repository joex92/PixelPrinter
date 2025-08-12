package me.zombie_striker.pixelprinter.util;

import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * A small help with reflection
 */
public class ReflectionUtil {
	private static final String SERVER_VERSION;

	static {
			String resolved = null;
			try {
					// e.g. org.bukkit.craftbukkit.v1_21_R1.CraftServer  OR  org.bukkit.craftbukkit.CraftServer
					String pkg = Bukkit.getServer().getClass().getPackage().getName();
					int i = pkg.indexOf("craftbukkit.");
					if (i >= 0) {
							String rest = pkg.substring(i + "craftbukkit.".length()); // "v1_21_R1" or "CraftServer"
							int dot = rest.indexOf('.');
							if (dot > 0 && rest.startsWith("v")) {
									resolved = rest.substring(0, dot); // "v1_21_R1"
							}
					}
			} catch (Throwable ignored) {
					// fall through to fallback below
			}

			if (resolved == null) {
					// Fallback for unversioned CraftBukkit packages (modern Paper/Purpur):
					// Build "v<major>_<minor>" from "1.21.8-R0.1-SNAPSHOT" -> "v1_21"
					String bv = Bukkit.getBukkitVersion();       // "1.21.8-R0.1-SNAPSHOT"
					String core = bv.split("-")[0];              // "1.21.8"
					String[] parts = core.split("\\.");          // ["1","21","8"]
					String major = parts.length > 0 ? parts[0] : "1";
					String minor = parts.length > 1 ? parts[1] : "0";
					resolved = "v" + major + "_" + minor;        // "v1_21"
			}

			SERVER_VERSION = resolved;
	}


	/**
	 * Returns true if the current server version is >= mainVersion.secondVersion.
	 * Example: isVersionHigherThan(1, 21) is true on 1.21.x (including R1/R2 etc).
	 */
	public static boolean isVersionHigherThan(int mainVersion, int secondVersion) {
			int[] cur = parseMajorMinor(SERVER_VERSION);
			int major = cur[0], minor = cur[1];
			if (major != mainVersion) {
					return major > mainVersion;
			}
			return minor >= secondVersion;
	}

	/** Parses "v1_21_R1", "v1_21", "1.21", or "1.21.8" into {major, minor}. */
	private static int[] parseMajorMinor(String v) {
			if (v == null) return new int[]{0, 0};
			String s = v.trim();
			if (s.isEmpty()) return new int[]{0, 0};

			// Strip optional leading 'v' (e.g., "v1_21_R1")
			if (s.charAt(0) == 'v' || s.charAt(0) == 'V') s = s.substring(1);

			// Normalize separators so we can split consistently
			s = s.replace('.', '_');

			String[] parts = s.split("_"); // ["1","21","R1"] or ["1","21","8"] or ["1","21"]
			int major = 0, minor = 0;
			try { if (parts.length > 0) major = Integer.parseInt(parts[0]); } catch (Exception ignored) {}
			try { if (parts.length > 1) minor = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
			return new int[]{major, minor};
	}


	/**
	 * Returns the NMS class.
	 *
	 * @param name The name of the class
	 * @return The NMS class or null if an error occurred
	 */
	public static Class<?> getNMSClass(String name) {
			// Legacy NMS (pre-1.17): net.minecraft.server.vX_Y_RZ.<name>
			try {
					return Class.forName("net.minecraft.server." + SERVER_VERSION + "." + name);
			} catch (ClassNotFoundException ignored) {
					// Post-1.17 Mojang-mapped packages don't use the old path.
					// Try a couple of reasonable fallbacks; if they fail, print a helpful error.
					try {
							// Rarely present, but cheap to try
							return Class.forName("net.minecraft.server." + name);
					} catch (ClassNotFoundException ignored2) {
							try {
									// Many classes now live directly under net.minecraft.*
									return Class.forName("net.minecraft." + name);
							} catch (ClassNotFoundException e3) {
									System.err.println("[PixelPrinter] NMS class not found: " + name +
													" (tried legacy and unversioned lookups on " + Bukkit.getVersion() + ")");
									e3.printStackTrace();
									return null;
							}
					}
			}
	}


	/**
	 * Returns the CraftBukkit class.
	 *
	 * @param name The name of the class
	 * @return The CraftBukkit class or null if an error occurred
	 */

	public static Class<?> getCraftbukkitClass(String name, String packageName) {
			// Try legacy versioned CraftBukkit first: org.bukkit.craftbukkit.vX_Y_RZ.<package>.<name>
			try {
					return Class.forName("org.bukkit.craftbukkit." + SERVER_VERSION + "." + packageName + "." + name);
			} catch (ClassNotFoundException ignored) {
					// Fallback: unversioned CraftBukkit (modern Paper/Purpur): org.bukkit.craftbukkit.<package>.<name>
					try {
							return Class.forName("org.bukkit.craftbukkit." + packageName + "." + name);
					} catch (ClassNotFoundException e2) {
							e2.printStackTrace();
							return null;
					}
			}
	}


	/**
	 * Returns the CraftBukkit class.
	 *
	 * @param name The name of the class
	 * @return The CraftBukkit class or null if an error occurred
	 */

	public static Class<?> getCraftbukkitClass(String name) {
		try {
			return Class.forName("org.bukkit.craftbukkit." + SERVER_VERSION
					+ "." + name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Returns the mojang.authlib class.
	 *
	 * @param name The name of the class
	 * @return The mojang.authlib class or null if an error occurred
	 */

	public static Class<?> getMojangAuthClass(String name) {
		try {
			return Class.forName("com.mojang.authlib." + name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Invokes the method
	 *
	 * @param handle           The handle to invoke it on
	 * @param methodName       The name of the method
	 * @param parameterClasses The parameter types
	 * @param args             The arguments
	 * @return The resulting object or null if an error occurred / the
	 * method didn't return a thing
	 */
	@SuppressWarnings("rawtypes")
	public static Object invokeMethod(Object handle, String methodName,
									  Class[] parameterClasses, Object... args) {
		return invokeMethod(handle.getClass(), handle, methodName,
				parameterClasses, args);
	}

	/**
	 * Invokes the method
	 *
	 * @param clazz            The class to invoke it from
	 * @param handle           The handle to invoke it on
	 * @param methodName       The name of the method
	 * @param parameterClasses The parameter types
	 * @param args             The arguments
	 * @return The resulting object or null if an error occurred / the
	 * method didn't return a thing
	 */
	@SuppressWarnings("rawtypes")
	public static Object invokeMethod(Class<?> clazz, Object handle,
									  String methodName, Class[] parameterClasses, Object... args) {
		Optional<Method> methodOptional = getMethod(clazz, methodName,
				parameterClasses);

		if (!methodOptional.isPresent()) {
			return null;
		}

		Method method = methodOptional.get();

		try {
			return method.invoke(handle, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Sets the value of an instance field
	 *
	 * @param handle The handle to invoke it on
	 * @param name   The name of the field
	 * @param value  The new value of the field
	 */
	@SuppressWarnings("deprecation")
	public static void setInstanceField(Object handle, String name,
										Object value) {
		Class<?> clazz = handle.getClass();
		Optional<Field> fieldOptional = getField(clazz, name);
		if (!fieldOptional.isPresent()) {
			return;
		}

		Field field = fieldOptional.get();
		if (!field.isAccessible()) {
			field.setAccessible(true);
		}
		try {
			field.set(handle, value);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the value of an instance field
	 *
	 * @param handle The handle to invoke it on
	 * @param name   The name of the field
	 * @return The result
	 */
	@SuppressWarnings("deprecation")
	public static Object getInstanceField(Object handle, String name) {
		Class<?> clazz = handle.getClass();
		Optional<Field> fieldOptional = getField(clazz, name);
		if (!fieldOptional.isPresent()) {
			return handle;
		}
		Field field = fieldOptional.get();
		if (!field.isAccessible()) {
			field.setAccessible(true);
		}
		try {
			return field.get(handle);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Returns an enum constant
	 *
	 * @param enumClass The class of the enum
	 * @param name      The name of the enum constant
	 * @return The enum entry or null
	 */
	public static Object getEnumConstant(Class<?> enumClass, String name) {
		if (!enumClass.isEnum()) {
			return null;
		}
		for (Object o : enumClass.getEnumConstants()) {
			if (name.equals(invokeMethod(o, "name", new Class[0]))) {
				return o;
			}
		}
		return null;
	}

	/**
	 * Returns the constructor
	 *
	 * @param clazz  The class
	 * @param params The Constructor parameters
	 * @return The Constructor or an empty Optional if there is none with
	 * these parameters
	 */
	public static Optional<?> getConstructor(Class<?> clazz,
											 Class<?>... params) {
		try {
			return Optional.of(clazz.getConstructor(params));
		} catch (NoSuchMethodException e) {
			try {
				return Optional.of(clazz.getDeclaredConstructor(params));
			} catch (NoSuchMethodException e2) {
				e2.printStackTrace();
			}
		}
		return Optional.empty();
	}

	/**
	 * Instantiates the class. Will print the errors it gets
	 *
	 * @param constructor The constructor
	 * @param arguments   The initial arguments
	 * @return The resulting object, or null if an error occurred.
	 */
	public static Object instantiate(Constructor<?> constructor,
									 Object... arguments) {
		try {
			return constructor.newInstance(arguments);
		} catch (InstantiationException | IllegalAccessException
				| InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Optional<Method> getMethod(Class<?> clazz, String name,
											 Class<?>... params) {
		try {
			return Optional.of(clazz.getMethod(name, params));
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		try {
			return Optional.of(clazz.getDeclaredMethod(name, params));
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}

	public static Optional<Field> getField(Class<?> clazz, String name) {
		try {
			return Optional.of(clazz.getField(name));
		} catch (NoSuchFieldException e) {
		}

		try {
			return Optional.of(clazz.getDeclaredField(name));
		} catch (NoSuchFieldException e) {
		}
		return Optional.empty();
	}
}