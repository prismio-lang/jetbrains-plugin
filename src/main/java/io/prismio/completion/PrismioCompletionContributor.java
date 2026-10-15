package io.prismio.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import io.prismio.PrismioFileType;
import io.prismio.icons.PrismioIcons;
import io.prismio.imports.PrismioImports;
import io.prismio.lang.PrismioWords;
import io.prismio.navigation.Declaration;
import io.prismio.navigation.DeclarationKind;
import io.prismio.navigation.PrismioScopeResolver;
import io.prismio.psi.PrismioFile;
import io.prismio.symbols.CompilerIntrinsics;
import io.prismio.symbols.FnSig;
import io.prismio.symbols.Member;
import io.prismio.symbols.ModuleSummary;
import io.prismio.symbols.ProjectSymbols;
import io.prismio.symbols.StdlibIndex;
import io.prismio.symbols.SymbolTable;
import io.prismio.symbols.TypeDecl;
import io.prismio.symbols.TypeInference;
import io.prismio.toolchain.PrismioToolchainService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Completion for Prismio.
 *
 * <p>What is offered depends on where the caret is ({@link CompletionPosition}),
 * and everything the language's library provides — functions, types, members,
 * modules — comes from the toolchain's own standard library through
 * {@link StdlibIndex}. Nothing here lists a standard-library name. A function
 * from a module the file does not import is still offered, marked with its
 * module, and choosing it adds the import.
 */
public final class PrismioCompletionContributor extends CompletionContributor {

  public PrismioCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
        new CompletionProvider<>() {
          @Override
          protected void addCompletions(@NotNull CompletionParameters parameters,
              @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
            if (parameters.getOriginalFile() instanceof PrismioFile) {
              complete(parameters, result);
            }
          }
        });
  }

  private static void complete(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    CharSequence text = parameters.getEditor().getDocument().getCharsSequence();
    int offset = parameters.getOffset();
    CompletionPosition position = CompletionPosition.at(text, offset);
    Project project = file.getProject();

    switch (position.kind()) {
      case NONE -> result.stopHere();
      case IMPORT -> {
        addImportCompletions(file, position, result);
        result.stopHere();
      }
      case IMPORT_FROM -> {
        Keywords.importFrom(result);
        result.stopHere();
      }
      case MEMBER -> {
        addMemberCompletions(file, text, position, result);
        result.stopHere();
      }
      case TYPE -> addTypeCompletions(file, result);
      case TOP_LEVEL -> {
        Keywords.topLevel(result);
        Keywords.templates(result);
      }
      case STATEMENT -> {
        addNames(parameters.getPosition(), file, result);
        addLibrary(project, file, result, true);
        Keywords.statement(result, position.afterBlock());
        Keywords.literals(result);
        Keywords.freestanding(result);
      }
      case EXPRESSION -> {
        addNames(parameters.getPosition(), file, result);
        addLibrary(project, file, result, true);
        Keywords.expression(result);
        Keywords.literals(result);
        Keywords.freestanding(result);
      }
    }
  }

  // ------------------------------------------------------------ names in scope

  /** Locals, parameters and the file's own declarations, with their signatures. */
  private static void addNames(@NotNull PsiElement position, @NotNull PsiFile file,
      @NotNull CompletionResultSet result) {
    ModuleSummary summary = ProjectSymbols.summary(file.getOriginalFile());
    Map<String, List<FnSig>> freeFunctions = new LinkedHashMap<>();
    Set<String> types = new HashSet<>();
    for (FnSig fn : summary.functions()) {
      if (fn.owner() == null) {
        freeFunctions.computeIfAbsent(fn.name(), k -> new ArrayList<>()).add(fn);
      }
    }
    for (TypeDecl type : summary.types()) {
      types.add(type.name());
    }
    Set<String> seen = new HashSet<>();
    for (Declaration decl : PrismioScopeResolver.collectVisibleDeclarations(position)) {
      String name = decl.getName();
      if (name.isEmpty() || name.equals(Lookups.DUMMY) || name.startsWith(Lookups.DUMMY)) {
        continue;
      }
      if (decl.getKind() == DeclarationKind.FUNCTION) {
        List<FnSig> overloads = freeFunctions.get(name);
        // A method inside an `impl` is reached through its receiver, not by bare name.
        if (overloads == null || !seen.add("fn:" + name)) {
          continue;
        }
        result.addElement(Lookups.function(overloads, "", false, 80));
        continue;
      }
      if (decl.getKind() == DeclarationKind.IMPLEMENTATION || !seen.add(decl.getKind() + ":" + name)) {
        continue;
      }
      double priority = switch (decl.getKind()) {
        case VARIABLE, PARAMETER -> 100;
        case CONSTANT -> 85;
        default -> 70;
      };
      result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name)
          .withIcon(decl.getKind().getIcon())
          .withTypeText(types.contains(name) ? decl.getKind().getDisplayName() : null), priority));
    }
  }

  /**
   * The standard library's free functions, types and module-level values, from
   * every module: the ones the file imports first, the rest marked with the
   * module that choosing them imports.
   */
  private static void addLibrary(@NotNull Project project, @NotNull PsiFile file,
      @NotNull CompletionResultSet result, boolean includeTypes) {
    StdlibIndex.Snapshot library = StdlibIndex.getInstance(project).get();
    if (library.isEmpty()) {
      return;
    }
    List<ModuleSummary.Import> imports = ProjectSymbols.summary(file.getOriginalFile()).imports();
    for (Map.Entry<String, List<FnSig>> entry : library.freeFunctions().entrySet()) {
      if (entry.getKey().startsWith("__")) {
        continue;
      }
      Map<String, List<FnSig>> byModule = new LinkedHashMap<>();
      for (FnSig fn : entry.getValue()) {
        byModule.computeIfAbsent(fn.module(), k -> new ArrayList<>()).add(fn);
      }
      for (Map.Entry<String, List<FnSig>> group : byModule.entrySet()) {
        boolean imported = PrismioImports.isImported(imports, group.getKey());
        result.addElement(Lookups.function(group.getValue(), imported ? "" : group.getKey(), true,
            imported ? 60 : 20));
      }
    }
    if (includeTypes) {
      for (TypeDecl type : library.types().values()) {
        boolean imported = PrismioImports.isImported(imports, type.module());
        result.addElement(Lookups.type(type, imported ? "" : type.module(), imported ? 55 : 15));
      }
    }
    for (ModuleSummary.Global global : library.globals().values()) {
      boolean imported = PrismioImports.isImported(imports, global.module());
      result.addElement(Lookups.global(global, imported ? "" : global.module(), imported ? 58 : 18));
    }
  }

  // ------------------------------------------------------------ members

  private static void addMemberCompletions(@NotNull PsiFile file, @NotNull CharSequence text,
      @NotNull CompletionPosition position, @NotNull CompletionResultSet result) {
    SymbolTable.Scope scope = ProjectSymbols.scope(file);
    List<ModuleSummary.Import> imports = ProjectSymbols.summary(file.getOriginalFile()).imports();
    TypeInference.Receiver receiver =
        TypeInference.receiverAt(text, position.prefixStart(), scope, imports);
    if (receiver == null) {
      return;
    }
    if (receiver.module() != null) {
      StdlibIndex.Snapshot library = StdlibIndex.getInstance(file.getProject()).get();
      ModuleSummary module = library.modules().get(receiver.module());
      if (module == null) {
        return;
      }
      Map<String, List<FnSig>> byName = new LinkedHashMap<>();
      for (FnSig fn : module.functions()) {
        if (fn.owner() == null && fn.visibleOutside() && !fn.name().startsWith("__")) {
          byName.computeIfAbsent(fn.name(), k -> new ArrayList<>()).add(fn);
        }
      }
      for (List<FnSig> overloads : byName.values()) {
        result.addElement(Lookups.function(overloads, "", true, 50));
      }
      return;
    }
    List<Member> members = receiver.isStatic()
        ? scope.staticMembers(receiver.type())
        : scope.instanceMembers(receiver.type());
    for (Member member : members) {
      boolean needsImport = !member.module().isEmpty() && member.module().startsWith("std.")
          && !PrismioImports.isImported(imports, member.module());
      result.addElement(Lookups.member(member, needsImport ? member.module() : ""));
    }
  }

  // ------------------------------------------------------------ types

  private static void addTypeCompletions(@NotNull PsiFile file, @NotNull CompletionResultSet result) {
    PrismioWords.BUILTIN_TYPES.stream().sorted().forEach(name ->
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name)
            .withIcon(AllIcons.Nodes.Type)
            .withTypeText("built-in"), 50)));
    // The generic types the compiler itself provides: those whose members it lowers.
    Set<String> builtIn = new HashSet<>();
    for (String owner : CompilerIntrinsics.owners(
        PrismioToolchainService.getInstance(file.getProject()).getCompilerExecutable())) {
      // `Ptr` is offered above as a built-in type; it is an owner only for `load`.
      if (!PrismioWords.BUILTIN_TYPES.contains(owner) && builtIn.add(owner)) {
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(owner)
            .withIcon(AllIcons.Nodes.Class)
            .withTailText("<>", true)
            .withTypeText("built-in")
            .withInsertHandler(Lookups.ANGLE_BRACKETS), 50));
      }
    }
    ModuleSummary summary = ProjectSymbols.summary(file.getOriginalFile());
    for (TypeDecl type : summary.types()) {
      result.addElement(Lookups.type(type, "", 70));
    }
    StdlibIndex.Snapshot library = StdlibIndex.getInstance(file.getProject()).get();
    for (TypeDecl type : library.types().values()) {
      boolean imported = PrismioImports.isImported(summary.imports(), type.module());
      result.addElement(Lookups.type(type, imported ? "" : type.module(), imported ? 55 : 20));
    }
  }

  // ------------------------------------------------------------ imports

  private static void addImportCompletions(@NotNull PsiFile file, @NotNull CompletionPosition position,
      @NotNull CompletionResultSet result) {
    Project project = file.getProject();
    StdlibIndex.Snapshot library = StdlibIndex.getInstance(project).get();
    List<ModuleSummary.Import> imports = ProjectSymbols.summary(file.getOriginalFile()).imports();

    if (position.importDirectory()) {
      result.addElement(Lookups.stdPackage(false));
      for (VirtualFile dir : projectPackageRoots(file)) {
        for (VirtualFile child : dir.getChildren()) {
          if (child.isDirectory() && !child.getName().startsWith(".")) {
            result.addElement(LookupElementBuilder.create(child.getName()).withIcon(AllIcons.Nodes.Package));
          }
        }
      }
      return;
    }

    if (position.importGroupDirectory() != null) {
      // `import {<caret>} from project`: the modules of that directory, exactly as
      // `import project.<caret>` lists them, but only modules — a group names files.
      addDirectoryModules(file, library, imports, position.importGroupDirectory(), true, result);
      return;
    }

    String qualifier = position.importQualifier();
    if (qualifier == null) {
      result.addElement(Lookups.stdPackage(true));
      for (ModuleSummary module : library.modules().values()) {
        result.addElement(Lookups.module(module, module.name(),
            PrismioImports.isImported(imports, module.name())));
      }
      addProjectModules(file, result);
      return;
    }

    ModuleSummary asModule = library.modules().get(qualifier);
    if (asModule != null) {
      // `import std.string.trim` names one declaration of the module.
      Set<String> seen = new HashSet<>();
      for (FnSig fn : asModule.functions()) {
        if (fn.owner() == null && fn.visibleOutside() && seen.add(fn.name())) {
          result.addElement(LookupElementBuilder.create(fn.name()).withIcon(AllIcons.Nodes.Function)
              .withTailText(fn.parameterText(false), true));
        }
      }
      for (TypeDecl type : asModule.types()) {
        result.addElement(LookupElementBuilder.create(type.name()).withIcon(Lookups.icon(type)));
      }
      return;
    }

    addDirectoryModules(file, library, imports, qualifier, false, result);
  }

  /**
   * What lies directly under {@code directory}: the toolchain's modules when it is
   * one of theirs, otherwise the project's own directory of that name.
   */
  private static void addDirectoryModules(@NotNull PsiFile file, @NotNull StdlibIndex.Snapshot library,
      @NotNull List<ModuleSummary.Import> imports, @NotNull String directory, boolean modulesOnly,
      @NotNull CompletionResultSet result) {
    boolean any = false;
    for (ModuleSummary module : library.modules().values()) {
      if (module.name().startsWith(directory + ".")) {
        String leaf = module.name().substring(directory.length() + 1);
        if (!leaf.contains(".")) {
          any = true;
          result.addElement(Lookups.module(module, leaf, PrismioImports.isImported(imports, module.name())));
        }
      }
    }
    if (any) {
      return;
    }
    VirtualFile dir = findPackageDirectory(file, directory);
    if (dir == null) {
      return;
    }
    VirtualFile current = file.getOriginalFile().getVirtualFile();
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory() && !modulesOnly && !child.getName().startsWith(".")) {
        result.addElement(LookupElementBuilder.create(child.getName())
            .withIcon(AllIcons.Nodes.Package).withTypeText("package"));
      } else if ("psm".equals(child.getExtension()) && !child.equals(current)) {
        result.addElement(LookupElementBuilder.create(child.getNameWithoutExtension())
            .withIcon(PrismioIcons.FILE).withTypeText("module"));
      }
    }
  }

  /**
   * The program's own modules, named as an import from its source root writes
   * them: siblings of this file, the source root's children, and every indexed
   * module by its dotted path.
   */
  private static void addProjectModules(@NotNull PsiFile file, @NotNull CompletionResultSet result) {
    Set<String> added = new HashSet<>();
    added.add("std");
    VirtualFile current = file.getOriginalFile().getVirtualFile();
    VirtualFile currentDir = current != null ? current.getParent() : null;
    List<VirtualFile> roots = new ArrayList<>();
    if (currentDir != null) {
      roots.add(currentDir);
    }
    roots.addAll(projectPackageRoots(file));
    for (VirtualFile dir : roots) {
      for (VirtualFile child : dir.getChildren()) {
        String name = child.getName();
        if (name.startsWith(".") || name.equals("build")) {
          continue;
        }
        if (child.isDirectory()) {
          if (added.add(name)) {
            result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name)
                .withIcon(AllIcons.Nodes.Package).withTypeText("package"), 30));
          }
        } else if ("psm".equals(child.getExtension()) && !child.equals(current)) {
          String module = child.getNameWithoutExtension();
          if (added.add(module)) {
            result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(module)
                .withIcon(PrismioIcons.FILE).withTypeText("module"), 40));
          }
        }
      }
    }
    Project project = file.getProject();
    java.nio.file.Path stdlib = StdlibIndex.getInstance(project).get().directory();
    String stdlibPath = stdlib == null ? null : stdlib.toString();
    for (VirtualFile vf : FileTypeIndex.getFiles(PrismioFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
      String path = vf.getPath();
      if (vf.equals(current) || path.contains("/.prismio/") || path.contains("/build/")
          || path.contains("/.git/") || stdlibPath != null && path.startsWith(stdlibPath)) {
        continue;
      }
      PsiFile psi = com.intellij.psi.PsiManager.getInstance(project).findFile(vf);
      String dotted = psi == null ? null : ProjectSymbols.moduleName(psi);
      if (dotted != null && !dotted.isEmpty() && added.add(dotted)) {
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(dotted)
            .withIcon(PrismioIcons.FILE).withTypeText("module"), 25));
      }
    }
  }

  private static List<VirtualFile> projectPackageRoots(@NotNull PsiFile file) {
    List<VirtualFile> roots = new ArrayList<>();
    for (VirtualFile root : ProjectRootManager.getInstance(file.getProject()).getContentRoots()) {
      VirtualFile src = root.findChild("src");
      roots.add(src != null ? src : root);
    }
    return roots;
  }

  private static VirtualFile findPackageDirectory(@NotNull PsiFile file, @NotNull String dotted) {
    String relative = dotted.replace('.', '/');
    VirtualFile current = file.getOriginalFile().getVirtualFile();
    if (current != null && current.getParent() != null) {
      VirtualFile child = current.getParent().findFileByRelativePath(relative);
      if (child != null && child.isDirectory()) {
        return child;
      }
    }
    for (VirtualFile root : ProjectRootManager.getInstance(file.getProject()).getContentRoots()) {
      for (VirtualFile base : new VirtualFile[] {root, root.findChild("src")}) {
        if (base == null) {
          continue;
        }
        VirtualFile child = base.findFileByRelativePath(relative);
        if (child != null && child.isDirectory()) {
          return child;
        }
      }
    }
    return null;
  }
}
