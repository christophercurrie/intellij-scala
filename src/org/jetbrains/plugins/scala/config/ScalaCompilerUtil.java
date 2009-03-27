package org.jetbrains.plugins.scala.config;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.plugins.scala.config.ScalaConfigUtils;
import org.jetbrains.plugins.scala.util.LibrariesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collection;

/**
 * @author ilyas
 */
public class ScalaCompilerUtil {
  public static final Condition<Library> SCALA_COMPILER_LIB_CONDITION = new Condition<Library>() {
  public boolean value(Library library) {
    return isScalaCompilerLibrary(library);
  }
};
  public static final String COMPILER_PROPERTIES_PATH = "compiler.properties";
  public static final String COMPILER_CLASS_PATH = "scala/tools/nsc/Main.class";
  public static final String SCALA_COMPILER_JAR_NAME_PREFIX = "scala-compiler";

  /**
   * Checks wheter given IDEA library contaions Scala Compiler classes
   */
  public static boolean isScalaCompilerLibrary(Library library) {
    return library != null && ScalaConfigUtils.checkLibrary(library, SCALA_COMPILER_JAR_NAME_PREFIX, COMPILER_CLASS_PATH);
  }

  public static Library[] getScalaCompilerLibrariesByModule(final Module module) {
    return LibrariesUtil.getLibrariesByCondition(module, SCALA_COMPILER_LIB_CONDITION);
  }

  @NotNull
  public static String getScalaCompilerJarPath(Module module) {
    if (module == null) return "";
    Library[] libraries = getScalaCompilerLibrariesByModule(module);
    if (libraries.length == 0) return "";
    final Library library = libraries[0];
    return getScalaCompilerJarPathForLibrary(library);
  }

  public static String getScalaCompilerJarPathForLibrary(Library library) {
    return ScalaConfigUtils.getSpecificJarForLibrary(library, SCALA_COMPILER_JAR_NAME_PREFIX, COMPILER_CLASS_PATH);
  }

  public static boolean isScalaCompilerSetUpForModule(Module module) {
    return getScalaCompilerJarPath(module).length() != 0;
  }

  public static String getScalaCompilerVersion(@NotNull String jarPath) {
    String jarVersion = ScalaConfigUtils.getScalaJarVersion(jarPath, COMPILER_PROPERTIES_PATH);
    return jarVersion != null ? jarVersion : ScalaConfigUtils.UNDEFINED_VERSION;
  }



  // Compiler libraries management
  public static Library[] getProjectScalaCompilerLibraries(Project project) {
    if (project == null) return new Library[0];
    final LibraryTable table = ProjectLibraryTable.getInstance(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), SCALA_COMPILER_LIB_CONDITION);
    return all.toArray(new Library[all.size()]);
  }

  public static Library[] getAllScalaCompilerLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalScalaCompilerLibraries(), getProjectScalaCompilerLibraries(project), Library.class);
  }


  public static Library[] getGlobalScalaCompilerLibraries() {
    return LibrariesUtil.getGlobalLibraries(SCALA_COMPILER_LIB_CONDITION);
  }

  public static String[] getScalaCompilerLibNames() {
    return LibrariesUtil.getLibNames(getGlobalScalaCompilerLibraries());
  }

  public static Collection<String> getScalaCompilerVersions(final Project project) {
    return ContainerUtil.map2List(getAllScalaCompilerLibraries(project), new Function<Library, String>() {
      public String fun(Library library) {
        return getScalaCompilerLibVersion(library);
      }
    });
  }

  public static String getScalaCompilerLibVersion(Library library) {
    return getScalaCompilerVersion(getScalaCompilerJarPathForLibrary(library));
  }


}