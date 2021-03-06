package nirusu.nirucmd;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import discord4j.core.object.entity.channel.Channel.Type;
import nirusu.nirucmd.annotation.Command;
import nirusu.nirucmd.exception.DuplicateKeysException;

/**
 * This class handles all commands
 *
 * This class loads all command modules on startup with the given packages.
 * After the modules are build, the commands can be invoked and executed with a given key.
 *
 * The command modules must extend {@link BaseModule}
 * The commands must annote {@link Command}
 */

public class CommandDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandDispatcher.class);
    private Set<Class<? extends BaseModule>> modules;

    public static Logger getLogger() {
        return LOGGER;
    }

    public static class Builder {
        private HashSet<String> packages;

        public Builder() {
            packages = new HashSet<>();
        }

        public Builder addPackage(@Nonnull String pkg) {
            packages.add(pkg);
            return this;
        }

        public CommandDispatcher build() {
            return new CommandDispatcher(this);
        }
    }

    /**
     * Load all module class from the parsed build {@link Builder#packages}
     * @param b the builder with the packages
     */
    private CommandDispatcher(Builder b) {
        this.modules = new HashSet<>();
        for (String pkg : b.packages) {
            Reflections ref = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(pkg))
                .setScanners(new SubTypesScanner())
                .filterInputsBy(new FilterBuilder().includePackage(pkg))
                );
            this.modules.addAll(ref.getSubTypesOf(nirusu.nirucmd.BaseModule.class));
        }
        checkForDuplicatedKeys();
    }


    /**
     * Finds a command with given key.
     * 
     * This will search for a fitting method in {@link #modules} and will invoke it.
     * 
     * The method must annote {@link nirusu.nirucmd.annotation.Command} and one of the keys in {@link nirusu.nirucmd.annotation.Command#key}
     * must be equal to @param key
     * 
     * @param ctx represents the current command context (event, args, etc...)
     * @throws NoSuchCommandException if no command could be found/wrong context
     */
    public CommandToRun getCommand(@Nonnull CommandContext ctx, @Nonnull String key) {
        // Create new instance of the module with the wanted method
        return getModuleWith(key)
            .map(module -> getMethodWith(module, key)
                .map(refl -> {
                    // set command context
                    module.setCommandContext(ctx);

                    // check if it gets executed in wrong context
                    boolean wrongContext = true;
                    for (Type context : refl.getAnnotation(Command.class).context()) {
                        if (ctx.isContext(context)) {
                            wrongContext = false;
                        }
                    }
                    // if so return invalid
                    if (wrongContext) {
                        return CommandToRun.getInvalid();
                    }
                    // else return the new command to run
                    return new CommandToRun(refl, module);})
                .orElse(CommandToRun.getInvalid()))
            .orElse(CommandToRun.getInvalid());
    }

    /**
     * Searches for a module with a method that gets triggert on given @param key
     * 
     * @return new instance of that module
     * @throws NoSuchCommandException if no method with such key could be found
     */
    private Optional<BaseModule> getModuleWith(@Nonnull String key) {
        for (Class<? extends BaseModule> md : modules) {
            if (hasMethodWith(md, key)) {
                try {
                    // try to create new instance of that module
                    return Optional.ofNullable(md.getConstructor().newInstance());
                } catch (IllegalAccessException | InstantiationException
                        | InvocationTargetException | NoSuchMethodException e ) {
                    LOGGER.error(String.format("Couldn't create module: %s", md.getSimpleName()), e);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Search for a method with given @param key in the given @param module and return it
     * 
     * @return the method with the given key
     * @throws NoSuchCommandException if no method was found
     */
    private Optional<Method> getMethodWith(@Nonnull BaseModule module, @Nonnull String key) {
        for (Method refl : module.getClass().getMethods()) {
            if (methodHasKey(refl, key)) {
                return Optional.of(refl);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if a given module @param module has a method with given key @param key
     * 
     * @return true if such a method exits else false
     */
    private boolean hasMethodWith(@Nonnull Class<? extends BaseModule> module, @Nonnull String key) {
        Method[] methods = module.getDeclaredMethods();
        for (Method refl : methods) {
            if (methodHasKey(refl, key)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Checks if a given method @param ref has the given key @param key
     * 
     * @return true if method annotes {@link nirusu.nirucmd.annotation.Command} and has the key else false
     */
    private boolean methodHasKey(@Nonnull Method ref, @Nonnull String key) {

        if (!ref.isAnnotationPresent(Command.class)) {
            return false;
        }

        for (String k : ref.getAnnotation(Command.class).key()) {
            if (k.equals(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if any methods have the same key {@link nirusu.nirucmd.annotation.Command#key}
     */
    private void checkForDuplicatedKeys() {
        List<Method> methods = getMethods();
        for (Method m : methods) {
            if (methods.stream().filter(o -> !o.equals(m)).anyMatch(o -> {
                for (String key : m.getAnnotation(Command.class).key()) {
                    if (Arrays.asList(o.getAnnotation(Command.class).key())
                        .stream().anyMatch(key::equals)) {
                        LOGGER.error(String.format("You have duplicated key in: %s and %s",m.getName(), o.getName()));
                        return true;
                    }
                }
                return false;
            })) {
                throw new DuplicateKeysException();
            }
        }

    }

    /**
     * Returns a list of all commands (Methods which annote {@link nirusu.nirucmd.annotation.Command})
     * 
     * @return all methods in {@link #modules} which annote {@link nirusu.nirucmd.annotation.Command}
     */
    public List<Method> getMethods() {
        return modules.stream().flatMap(module 
            -> Arrays.asList(module.getDeclaredMethods()).stream().filter(m 
            -> m.isAnnotationPresent(Command.class))).collect(Collectors.toList());
    }

    /**
     * Retuns a set of all modules classes
     */
    public Set<Class<? extends BaseModule>> getModules() {
        return this.modules;
    }
}
