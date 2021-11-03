package thito.nodeflow.internal.plugin;

import com.sun.javafx.css.StyleManager;
import thito.nodeflow.config.MapSection;
import thito.nodeflow.config.Section;
import thito.nodeflow.internal.NodeFlow;
import thito.nodeflow.internal.editor.Editor;
import thito.nodeflow.internal.editor.EditorManager;
import thito.nodeflow.internal.language.Language;
import thito.nodeflow.internal.plugin.event.Event;
import thito.nodeflow.internal.plugin.event.EventHandler;
import thito.nodeflow.internal.plugin.event.EventListener;
import thito.nodeflow.internal.plugin.event.Listener;
import thito.nodeflow.internal.project.Project;
import thito.nodeflow.internal.project.ProjectExport;
import thito.nodeflow.internal.project.module.FileModule;
import thito.nodeflow.internal.project.module.UnknownFileModule;
import thito.nodeflow.internal.resource.Resource;
import thito.nodeflow.internal.task.TaskThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginManager {
    private static PluginManager pluginManager = new PluginManager();

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    private List<Plugin> pluginList = new ArrayList<>();
    private List<ProjectExport> exporter = new ArrayList<>();
    private List<FileModule> moduleList = new ArrayList<>();
    private Map<Plugin, List<String>> styleSheetMap = new HashMap<>();
    private List<ProjectHandlerRegistry> projectHandlerRegistryList = new ArrayList<>();
    private ArrayList<EventListener> listeners = new ArrayList<>();
    private UnknownFileModule unknownFileModule = new UnknownFileModule();

    public PluginManager() {
        moduleList.add(new DirectoryFileModule());
    }

    public List<FileModule> getModuleList() {
        return Collections.unmodifiableList(moduleList);
    }

    public List<ProjectHandlerRegistry> getProjectHandlerRegistryList() {
        return Collections.unmodifiableList(projectHandlerRegistryList);
    }

    public void registerProjectHandlerRegistry(ProjectHandlerRegistry registry) {
        if (projectHandlerRegistryList.contains(registry)) throw new IllegalArgumentException("already registered");
        projectHandlerRegistryList.add(registry);
        TaskThread.UI().schedule(() -> {
            for (Editor editor : EditorManager.getActiveEditors()) {
                Project project = editor.projectProperty().get();
                if (project != null) {
                    TaskThread.BG().schedule(() -> {
                        project.getProjectHandlers().add(registry.loadHandler(project, project.getConfiguration().getOrCreateMap("handlers."+registry.getId())));
                    });
                }
            }
        });
    }

    public boolean isProjectHandlerRegistered(ProjectHandlerRegistry registry) {
        return projectHandlerRegistryList.contains(registry);
    }

    public void unregisterProjectHandlerRegistry(ProjectHandlerRegistry registry) {
        if (!projectHandlerRegistryList.contains(registry)) throw new IllegalArgumentException("not registered");
        projectHandlerRegistryList.remove(registry);
        TaskThread.UI().schedule(() -> {
            for (Editor editor : EditorManager.getActiveEditors()) {
                Project project = editor.projectProperty().get();
                if (project != null) {
                    project.getProjectHandlers().removeIf(x -> x.getRegistry() == registry);
                }
            }
        });
    }

    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableList(pluginList);
    }

    public void loadPluginLocale(Language target, Plugin plugin, InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            MapSection section = Section.parseToMap(reader);
            // find an existing language
            Language finalTarget = target;
            target = NodeFlow.getInstance().getAvailableLanguages().stream().filter(x -> x.getCode().equals(finalTarget.getCode())).findAny().orElse(target);
            target.loadLanguage(section, plugin.getId());
        }
    }

    public FileModule getModule(Resource resource) {
        for (FileModule module : moduleList) {
            if (module.acceptResource(resource)) {
                return module;
            }
        }
        return unknownFileModule;
    }

    public List<ProjectExport> getExporter() {
        return Collections.unmodifiableList(exporter);
    }

    public void registerFileModule(FileModule module) {
        moduleList.add(module);
    }

    public void unregisterFileModule(FileModule module) {
        moduleList.remove(module);
    }

    public void unregisterFileModule(Plugin plugin) {
        moduleList.removeIf(module -> Plugin.getPlugin(module.getClass()) == plugin);
    }

    public void registerExporter(ProjectExport export) {
        exporter.add(export);
    }

    public void unregisterExporter(ProjectExport export) {
        exporter.remove(export);
    }

    public void unregisterExporter(Plugin plugin) {
        exporter.removeIf(export -> Plugin.getPlugin(export.getClass()) == plugin);
    }

    public void registerListener(EventListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(EventListener listener) {
        listeners.remove(listener);
    }

    public void registerListener(Listener listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            method.setAccessible(true);
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler != null && method.getParameterCount() == 1 && Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                int index = 0;
                for (int i = 0; i < listeners.size(); i++) {
                    if (listeners.get(i).getPriority() == handler.priority()) {
                        index = i;
                        break;
                    }
                }
                listeners.add(index, new EventListener(listener, handler.priority(), handler.ignoreCancelled()) {
                    @Override
                    protected void handle(Event event) {
                        try {
                            method.invoke(listener, event);
                        } catch (IllegalAccessException e) {
                            Logger.getLogger(listener.getClass().getName()).log(Level.SEVERE, "Failed to access listener");
                        } catch (InvocationTargetException e) {
                            Logger.getLogger(listener.getClass().getName()).log(Level.SEVERE, "Failed to execute listener", e.getCause());
                        }
                    }
                });
            }
        }
    }

    public void unregisterListener(Listener listener) {
        listeners.removeIf(l -> l.getListener() == listener);
    }

    public <T extends Event> T fireEvent(T event) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).handleEvent(event);
        }
        return event;
    }

    public void addStyleSheet(Plugin plugin, String path) {
        styleSheetMap.computeIfAbsent(plugin, x -> new ArrayList<>()).add(path);
        for (List<String> other : styleSheetMap.values()) {
            if (other.contains(path)) {
                return;
            }
        }
        StyleManager.getInstance().addUserAgentStylesheet(path);
    }

    public void removeStyleSheet(Plugin plugin) {
        List<String> styleList = styleSheetMap.get(plugin);
        if (styleList != null) {
            for (String path : new ArrayList<>(styleList)) {
                removeStyleSheet(plugin, path);
            }
        }
    }

    public void removeStyleSheet(Plugin plugin, String path) {
        List<String> styleList = styleSheetMap.get(plugin);
        if (styleList != null) {
            if (styleList.remove(path)) {
                if (styleList.isEmpty()) styleSheetMap.remove(plugin);
                for (List<String> other : styleSheetMap.values()) {
                    if (other.contains(path)) {
                        return;
                    }
                }
                StyleManager.getInstance().removeUserAgentStylesheet(path);
            }
        }
    }

}
