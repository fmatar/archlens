package io.slixes.archlens.scanner;

import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ClassNode.Stereotype;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.FieldNode;
import io.slixes.archlens.domain.graph.MethodNode;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class TypeScriptAstScanner implements LanguageScanner {

  private static final Pattern IMPORT_FROM_PATTERN =
      Pattern.compile(
          "^\\s*import\\s+(?:type\\s+)?(.+?)\\s+from\\s+['\"]([^'\"]+)['\"]\\s*;?\\s*$");
  private static final Pattern IMPORT_SIDE_EFFECT_PATTERN =
      Pattern.compile("^\\s*import\\s+['\"]([^'\"]+)['\"]\\s*;?\\s*$");
  private static final Pattern EXPORT_FROM_PATTERN =
      Pattern.compile(
          "^\\s*export\\s+(?:type\\s+)?(\\*\\s+as\\s+[a-zA-Z0-9_$]+|\\*|\\{[^}]*\\})\\s*from\\s+['\"]([^'\"]+)['\"]\\s*;?\\s*$");
  private static final Pattern REQUIRE_CALL_PATTERN =
      Pattern.compile("\\brequire\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
  private static final Pattern DYNAMIC_IMPORT_PATTERN =
      Pattern.compile("\\bimport\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
  private static final Pattern CLASS_PATTERN =
      Pattern.compile(
          "^\\s*(?:export\\s+)?(?:default\\s+)?(abstract\\s+)?class\\s+([a-zA-Z0-9_$]+)(?:\\s*<[^{]*>)?(?:\\s+extends\\s+([a-zA-Z0-9_$.]+)(?:\\s*<[^{]*>)?)?(?:\\s+implements\\s+([^{]+))?");
  private static final Pattern INTERFACE_PATTERN =
      Pattern.compile(
          "^\\s*(?:export\\s+)?(?:default\\s+)?interface\\s+([a-zA-Z0-9_$]+)(?:\\s*<[^{]*>)?(?:\\s+extends\\s+([^{]+))?");
  private static final Pattern ENUM_PATTERN =
      Pattern.compile("^\\s*(?:export\\s+)?(?:const\\s+)?enum\\s+([a-zA-Z0-9_$]+)");
  private static final Pattern FUNCTION_PATTERN =
      Pattern.compile(
          "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s+([a-zA-Z0-9_$]+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)(?:\\s*:\\s*([^{;]+))?");
  private static final Pattern METHOD_PATTERN =
      Pattern.compile(
          "^\\s*(?:(?:public|private|protected|readonly|static|abstract|override|async)\\s+)*([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)(?:\\s*:\\s*([^{;=]+))?\\s*(?:\\{|;|$)");
  private static final Set<String> NON_METHOD_KEYWORDS =
      Set.of(
          "if",
          "for",
          "while",
          "switch",
          "catch",
          "function",
          "return",
          "typeof",
          "delete",
          "await",
          "yield",
          "import",
          "export",
          "constructor",
          "super");

  private static class TypeDeclaration {
    final String name;
    final Stereotype stereotype;
    final String extendsType;
    final List<String> implementsTypes;
    final List<FieldNode> fields = new ArrayList<>();
    final List<MethodNode> methods = new ArrayList<>();

    TypeDeclaration(
        String name, Stereotype stereotype, String extendsType, List<String> implementsTypes) {
      this.name = name;
      this.stereotype = stereotype;
      this.extendsType = extendsType;
      this.implementsTypes = implementsTypes;
    }
  }

  private record ImportRef(String moduleSpecifier, List<String> symbols) {}

  private record ParsedTsFile(
      Path file,
      String relPath,
      String currentModule,
      String directoryModule,
      String packageName,
      List<TypeDeclaration> types,
      List<MethodNode> moduleFunctions,
      List<ImportRef> imports,
      Map<String, String> barrelSymbolToModule,
      List<String> barrelWildcardModules) {}

  @Override
  public String languageId() {
    return "typescript";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null
        && ("typescript".equalsIgnoreCase(policy.lang())
            || "javascript".equalsIgnoreCase(policy.lang()))) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return false;
    }
    File root = new File(projectRoot);
    return new File(root, "tsconfig.json").exists()
        || new File(root, "package.json").exists()
        || new File(root, "jsconfig.json").exists();
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {
    return scanProject(projectRoot, srcRelativePath, basePrefix, null);
  }

  @Override
  public ScanResult scanProject(
      String projectRoot, String srcRelativePath, String basePrefix, ArchitecturePolicy policy)
      throws IOException {
    File scanDir = LanguageScanner.resolveScanDirectory(projectRoot, srcRelativePath, "src");
    if (!scanDir.exists()) {
      return new ScanResult(List.of(), List.of());
    }

    Path rootPath = new File(projectRoot != null ? projectRoot : ".").toPath();
    Set<String> omitPatterns = LanguageScanner.extractOmitPatterns(policy);
    List<Path> tsFiles = discoverTypeScriptFiles(scanDir.toPath(), rootPath, omitPatterns);
    Map<String, String> pathAliases = loadTsConfigPathAliases(rootPath, srcRelativePath);

    List<ParsedTsFile> parsedFiles = new ArrayList<>();
    Map<String, List<String>> moduleToTypeNames = new LinkedHashMap<>();
    Map<String, Map<String, String>> barrelExportsByModule = new LinkedHashMap<>();
    Map<String, List<String>> barrelWildcardsByModule = new LinkedHashMap<>();

    for (Path tsFile : tsFiles) {
      String rel = rootPath.relativize(tsFile).toString().replace('\\', '/');
      String currentModule = toTsModuleId(rel);
      String directoryModule = getDirectoryModule(rel, currentModule);
      String packageName = getPackageName(currentModule);

      List<String> rawLines = Files.readAllLines(tsFile, StandardCharsets.UTF_8);
      List<String> statements = extractLogicalTsStatements(rawLines);

      List<TypeDeclaration> types = new ArrayList<>();
      List<MethodNode> moduleFunctions = new ArrayList<>();
      List<ImportRef> imports = new ArrayList<>();
      Map<String, String> barrelSymbolToModule = new LinkedHashMap<>();
      List<String> barrelWildcardModules = new ArrayList<>();

      TypeDeclaration activeType = null;
      int activeTypeBraceDepth = 0;

      for (String stmt : statements) {
        String trimmed = stmt.trim();
        if (trimmed.isEmpty()) {
          continue;
        }

        Matcher sideEffectMatcher = IMPORT_SIDE_EFFECT_PATTERN.matcher(trimmed);
        if (sideEffectMatcher.find()) {
          imports.add(new ImportRef(sideEffectMatcher.group(1).trim(), List.of()));
          continue;
        }

        Matcher importMatcher = IMPORT_FROM_PATTERN.matcher(trimmed);
        if (importMatcher.find()) {
          String clause = importMatcher.group(1).trim();
          String spec = importMatcher.group(2).trim();
          List<String> symbols = parseImportClauseSymbols(clause);
          imports.add(new ImportRef(spec, symbols));
          continue;
        }

        Matcher exportMatcher = EXPORT_FROM_PATTERN.matcher(trimmed);
        if (exportMatcher.find()) {
          String clause = exportMatcher.group(1).trim();
          String spec = exportMatcher.group(2).trim();
          List<String> symbols = parseExportClauseSymbols(clause);
          imports.add(new ImportRef(spec, symbols));
          String resolvedTarget = resolveTsImport(directoryModule, spec, pathAliases);
          if (clause.equals("*")) {
            barrelWildcardModules.add(resolvedTarget);
          } else if (clause.startsWith("* as ")) {
            String ns = clause.substring(5).trim();
            if (!ns.isEmpty()) {
              barrelSymbolToModule.put(ns, resolvedTarget);
            }
          } else {
            parseBarrelExportMappings(clause, resolvedTarget, barrelSymbolToModule);
          }
          continue;
        }

        Matcher reqMatcher = REQUIRE_CALL_PATTERN.matcher(trimmed);
        while (reqMatcher.find()) {
          imports.add(new ImportRef(reqMatcher.group(1).trim(), List.of()));
        }

        Matcher dynMatcher = DYNAMIC_IMPORT_PATTERN.matcher(trimmed);
        while (dynMatcher.find()) {
          imports.add(new ImportRef(dynMatcher.group(1).trim(), List.of()));
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(trimmed);
        if (classMatcher.find()) {
          boolean isAbstract = classMatcher.group(1) != null;
          String clsName = classMatcher.group(2).trim();
          String extendsCls = classMatcher.group(3) != null ? classMatcher.group(3).trim() : null;
          List<String> implList = new ArrayList<>();
          String impls = classMatcher.group(4);
          if (impls != null && !impls.isBlank()) {
            for (String impl : splitTopLevelCommas(impls)) {
              String cleanImpl = stripGenerics(impl.trim());
              if (!cleanImpl.isBlank()) {
                implList.add(cleanImpl);
              }
            }
          }
          activeType =
              new TypeDeclaration(
                  clsName,
                  isAbstract ? Stereotype.ABSTRACT : Stereotype.CLASS,
                  extendsCls,
                  implList);
          types.add(activeType);
          activeTypeBraceDepth = countBraceDelta(trimmed);
          if (activeTypeBraceDepth <= 0) {
            activeType = null;
            activeTypeBraceDepth = 0;
          }
          continue;
        }

        Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(trimmed);
        if (interfaceMatcher.find()) {
          String ifaceName = interfaceMatcher.group(1).trim();
          List<String> extendsList = new ArrayList<>();
          String ext = interfaceMatcher.group(2);
          if (ext != null && !ext.isBlank()) {
            for (String item : splitTopLevelCommas(ext)) {
              String cleanExt = stripGenerics(item.trim());
              if (!cleanExt.isBlank()) {
                extendsList.add(cleanExt);
              }
            }
          }
          activeType =
              new TypeDeclaration(
                  ifaceName,
                  Stereotype.INTERFACE,
                  extendsList.isEmpty() ? null : extendsList.get(0),
                  List.of());
          types.add(activeType);
          activeTypeBraceDepth = countBraceDelta(trimmed);
          if (activeTypeBraceDepth <= 0) {
            activeType = null;
            activeTypeBraceDepth = 0;
          }
          continue;
        }

        Matcher enumMatcher = ENUM_PATTERN.matcher(trimmed);
        if (enumMatcher.find()) {
          String enumName = enumMatcher.group(1).trim();
          activeType = new TypeDeclaration(enumName, Stereotype.ENUM, null, List.of());
          types.add(activeType);
          activeTypeBraceDepth = countBraceDelta(trimmed);
          if (activeTypeBraceDepth <= 0) {
            activeType = null;
            activeTypeBraceDepth = 0;
          }
          continue;
        }

        Matcher fnMatcher = FUNCTION_PATTERN.matcher(trimmed);
        if (fnMatcher.find()) {
          String fnName = fnMatcher.group(1).trim();
          List<String> args = parseParameters(fnMatcher.group(2));
          String returnType =
              fnMatcher.group(3) != null && !fnMatcher.group(3).isBlank()
                  ? fnMatcher.group(3).trim()
                  : "void";
          MethodNode methodNode =
              new MethodNode(fnName, args, returnType, false, 1, 1.0, 1.0, 0, 0, 0, 1);
          if (activeType != null) {
            activeType.methods.add(methodNode);
          } else {
            moduleFunctions.add(methodNode);
          }
          if (activeType != null) {
            activeTypeBraceDepth += countBraceDelta(trimmed);
            if (activeTypeBraceDepth <= 0) {
              activeType = null;
              activeTypeBraceDepth = 0;
            }
          }
          continue;
        }

        if (activeType != null && activeType.stereotype != Stereotype.ENUM) {
          Matcher methodMatcher = METHOD_PATTERN.matcher(trimmed);
          if (methodMatcher.find()) {
            String methodName = methodMatcher.group(1).trim();
            if (!NON_METHOD_KEYWORDS.contains(methodName)) {
              List<String> args = parseParameters(methodMatcher.group(2));
              String returnType =
                  methodMatcher.group(3) != null && !methodMatcher.group(3).isBlank()
                      ? methodMatcher.group(3).trim()
                      : "void";
              activeType.methods.add(
                  new MethodNode(methodName, args, returnType, false, 1, 1.0, 1.0, 0, 0, 0, 1));
            }
          }
          activeTypeBraceDepth += countBraceDelta(trimmed);
          if (activeTypeBraceDepth <= 0) {
            activeType = null;
            activeTypeBraceDepth = 0;
          }
        }
      }

      List<String> typeNames = types.stream().map(t -> t.name).toList();
      moduleToTypeNames.put(currentModule, typeNames);
      if (!barrelSymbolToModule.isEmpty()) {
        barrelExportsByModule.put(currentModule, barrelSymbolToModule);
      }
      if (!barrelWildcardModules.isEmpty()) {
        barrelWildcardsByModule.put(currentModule, barrelWildcardModules);
      }

      parsedFiles.add(
          new ParsedTsFile(
              tsFile,
              rel,
              currentModule,
              directoryModule,
              packageName,
              types,
              moduleFunctions,
              imports,
              barrelSymbolToModule,
              barrelWildcardModules));
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();

    for (ParsedTsFile parsed : parsedFiles) {
      String absPath = parsed.file().toAbsolutePath().toString();
      List<String> sourceNodeIds = new ArrayList<>();

      if (parsed.types().isEmpty()) {
        String modName =
            new File(parsed.relPath())
                .getName()
                .replaceAll("\\.(svelte\\.ts|svelte\\.js|ts|tsx|js|jsx|svelte)$", "");
        classes.add(
            LanguageScanner.createDefaultClassNode(
                parsed.currentModule(),
                modName,
                parsed.packageName(),
                absPath,
                List.of(),
                parsed.moduleFunctions()));
        sourceNodeIds.add(parsed.currentModule());
      } else {
        for (TypeDeclaration typeDecl : parsed.types()) {
          String classId = parsed.currentModule() + "." + typeDecl.name;
          sourceNodeIds.add(classId);
          List<MethodNode> combinedMethods = new ArrayList<>(typeDecl.methods);
          if (parsed.types().size() == 1 && !parsed.moduleFunctions().isEmpty()) {
            combinedMethods.addAll(parsed.moduleFunctions());
          }
          classes.add(
              new ClassNode(
                  classId,
                  typeDecl.name,
                  parsed.packageName(),
                  absPath,
                  typeDecl.stereotype,
                  false,
                  null,
                  null,
                  1.0,
                  1,
                  0,
                  0,
                  0,
                  typeDecl.fields,
                  combinedMethods));

          if (typeDecl.extendsType != null && !typeDecl.extendsType.isBlank()) {
            edges.add(
                new DependencyEdge(
                    classId,
                    typeDecl.extendsType,
                    DependencyEdge.Kind.INHERITANCE,
                    "extends",
                    false));
          }
          for (String impl : typeDecl.implementsTypes) {
            edges.add(
                new DependencyEdge(
                    classId, impl, DependencyEdge.Kind.IMPLEMENTS, "implements", false));
          }
        }
      }

      Set<String> emittedEdges = new LinkedHashSet<>();
      for (ImportRef importRef : parsed.imports()) {
        String resolvedMod =
            resolveTsImport(parsed.directoryModule(), importRef.moduleSpecifier(), pathAliases);
        List<String> targetNodeIds =
            resolveTargetNodeIds(
                resolvedMod,
                importRef.symbols(),
                moduleToTypeNames,
                barrelExportsByModule,
                barrelWildcardsByModule);

        for (String fromId : sourceNodeIds) {
          for (String toId : targetNodeIds) {
            if (!fromId.equals(toId) && emittedEdges.add(fromId + "->" + toId)) {
              edges.add(
                  new DependencyEdge(fromId, toId, DependencyEdge.Kind.DEPENDENCY, null, false));
            }
          }
        }
        // Also emit module-level edge when class-level nodes are present so module-level queries
        // and
        // hybrid resolvers both match deterministically
        if ((!sourceNodeIds.contains(parsed.currentModule())
                || !targetNodeIds.contains(resolvedMod))
            && !parsed.currentModule().equals(resolvedMod)
            && emittedEdges.add(parsed.currentModule() + "->" + resolvedMod)) {
          edges.add(
              new DependencyEdge(
                  parsed.currentModule(),
                  resolvedMod,
                  DependencyEdge.Kind.DEPENDENCY,
                  null,
                  false));
        }
      }
    }

    return new ScanResult(classes, edges);
  }

  private List<String> resolveTargetNodeIds(
      String initialModule,
      List<String> importedSymbols,
      Map<String, List<String>> moduleToTypeNames,
      Map<String, Map<String, String>> barrelExportsByModule,
      Map<String, List<String>> barrelWildcardsByModule) {
    List<String> targets = new ArrayList<>();

    if (!importedSymbols.isEmpty()) {
      for (String symbol : importedSymbols) {
        String resolvedModule =
            resolveBarrelSymbolModule(
                initialModule,
                symbol,
                moduleToTypeNames,
                barrelExportsByModule,
                barrelWildcardsByModule,
                0);
        List<String> targetTypes = moduleToTypeNames.get(resolvedModule);
        if (targetTypes != null && targetTypes.contains(symbol)) {
          targets.add(resolvedModule + "." + symbol);
        } else if (targetTypes != null && targetTypes.size() == 1) {
          targets.add(resolvedModule + "." + targetTypes.get(0));
        } else {
          targets.add(resolvedModule);
        }
      }
    } else {
      List<String> targetTypes = moduleToTypeNames.get(initialModule);
      if (targetTypes != null && !targetTypes.isEmpty()) {
        for (String t : targetTypes) {
          targets.add(initialModule + "." + t);
        }
      } else {
        targets.add(initialModule);
      }
    }

    return targets.isEmpty() ? List.of(initialModule) : targets;
  }

  private String resolveBarrelSymbolModule(
      String moduleId,
      String symbol,
      Map<String, List<String>> moduleToTypeNames,
      Map<String, Map<String, String>> barrelExportsByModule,
      Map<String, List<String>> barrelWildcardsByModule,
      int depth) {
    if (depth >= 4) {
      return moduleId;
    }
    Map<String, String> directExports = barrelExportsByModule.get(moduleId);
    if (directExports != null && directExports.containsKey(symbol)) {
      String nextMod = directExports.get(symbol);
      if (!nextMod.equals(moduleId)) {
        return resolveBarrelSymbolModule(
            nextMod,
            symbol,
            moduleToTypeNames,
            barrelExportsByModule,
            barrelWildcardsByModule,
            depth + 1);
      }
    }
    List<String> localTypes = moduleToTypeNames.get(moduleId);
    if (localTypes != null && localTypes.contains(symbol)) {
      return moduleId;
    }
    List<String> wildcards = barrelWildcardsByModule.get(moduleId);
    if (wildcards != null) {
      for (String wcMod : wildcards) {
        if (wcMod.equals(moduleId)) continue;
        String candidate =
            resolveBarrelSymbolModule(
                wcMod,
                symbol,
                moduleToTypeNames,
                barrelExportsByModule,
                barrelWildcardsByModule,
                depth + 1);
        List<String> candTypes = moduleToTypeNames.get(candidate);
        if (candTypes != null && candTypes.contains(symbol)) {
          return candidate;
        }
      }
    }
    return moduleId;
  }

  static List<String> extractLogicalTsStatements(List<String> rawLines) {
    List<String> strippedLines = new ArrayList<>();
    boolean inBlockComment = false;

    for (String line : rawLines) {
      StringBuilder clean = new StringBuilder();
      boolean inSingle = false;
      boolean inDouble = false;
      boolean inTemplate = false;
      int i = 0;
      int len = line.length();

      while (i < len) {
        if (inBlockComment) {
          int endIdx = line.indexOf("*/", i);
          if (endIdx == -1) {
            i = len;
          } else {
            inBlockComment = false;
            i = endIdx + 2;
          }
          continue;
        }

        char c = line.charAt(i);
        if ((inSingle || inDouble || inTemplate) && c == '\\' && i + 1 < len) {
          clean.append(c).append(line.charAt(i + 1));
          i += 2;
          continue;
        }

        if (!inDouble && !inTemplate && c == '\'') {
          inSingle = !inSingle;
          clean.append(c);
          i++;
          continue;
        }
        if (!inSingle && !inTemplate && c == '"') {
          inDouble = !inDouble;
          clean.append(c);
          i++;
          continue;
        }
        if (!inSingle && !inDouble && c == '`') {
          inTemplate = !inTemplate;
          clean.append(c);
          i++;
          continue;
        }

        if (!inSingle && !inDouble && !inTemplate && i + 1 < len) {
          char next = line.charAt(i + 1);
          if (c == '/' && next == '/') {
            break;
          }
          if (c == '/' && next == '*') {
            inBlockComment = true;
            i += 2;
            continue;
          }
        }

        clean.append(c);
        i++;
      }

      String trimmed = clean.toString().trim();
      if (!trimmed.isEmpty()) {
        strippedLines.add(trimmed);
      }
    }

    List<String> logicalStatements = new ArrayList<>();
    StringBuilder accumulator = new StringBuilder();
    boolean accumulatingImportOrExport = false;

    for (String line : strippedLines) {
      if (!accumulatingImportOrExport) {
        if (isIncompleteImportOrExport(line)) {
          accumulatingImportOrExport = true;
          accumulator.setLength(0);
          accumulator.append(line);
        } else {
          logicalStatements.add(line);
        }
      } else {
        accumulator.append(' ').append(line);
        String combined = accumulator.toString();
        if (!isIncompleteImportOrExport(combined)) {
          logicalStatements.add(combined);
          accumulator.setLength(0);
          accumulatingImportOrExport = false;
        }
      }
    }

    if (accumulator.length() > 0) {
      logicalStatements.add(accumulator.toString());
    }

    return logicalStatements;
  }

  private static boolean isIncompleteImportOrExport(String text) {
    String trimmed = text.trim();
    if (trimmed.matches("^import\\s*(?:['\"].*|\\(.*)")) {
      return false;
    }
    if (trimmed.matches("^import(?:\\s+|\\{).*")) {
      if (countBraceDelta(trimmed) > 0) {
        return true;
      }
      return !trimmed.matches(".*\\bfrom\\s*['\"][^'\"]+['\"]\\s*;?\\s*$")
          && !trimmed.endsWith(";");
    }
    if (trimmed.matches("^export\\s+(?:type\\s+)?(?:\\*|\\{).*")) {
      if (countBraceDelta(trimmed) > 0) {
        return true;
      }
      if (trimmed.matches(".*\\bfrom\\s*$") || trimmed.matches(".*\\bfrom\\s+['\"][^'\"]*$")) {
        return true;
      }
    }
    return false;
  }

  private static int countBraceDelta(String text) {
    int delta = 0;
    boolean inSingle = false;
    boolean inDouble = false;
    boolean inTemplate = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length()) {
        i++;
        continue;
      }
      if (!inDouble && !inTemplate && c == '\'') inSingle = !inSingle;
      else if (!inSingle && !inTemplate && c == '"') inDouble = !inDouble;
      else if (!inSingle && !inDouble && c == '`') inTemplate = !inTemplate;
      else if (!inSingle && !inDouble && !inTemplate) {
        if (c == '{') delta++;
        else if (c == '}') delta--;
      }
    }
    return delta;
  }

  private static List<String> parseImportClauseSymbols(String clause) {
    List<String> symbols = new ArrayList<>();
    String remaining = clause;

    int openBrace = remaining.indexOf('{');
    int closeBrace = remaining.lastIndexOf('}');
    if (openBrace != -1 && closeBrace > openBrace) {
      String namedPart = remaining.substring(openBrace + 1, closeBrace);
      for (String raw : namedPart.split(",")) {
        String sym = extractOriginalSymbol(raw);
        if (sym != null) {
          symbols.add(sym);
        }
      }
      remaining =
          (remaining.substring(0, openBrace) + " " + remaining.substring(closeBrace + 1)).trim();
    }

    for (String raw : remaining.split(",")) {
      String trimmed = raw.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("*")) {
        continue;
      }
      String sym = extractOriginalSymbol(trimmed);
      if (sym != null) {
        symbols.add(sym);
      }
    }
    return symbols;
  }

  private static List<String> parseExportClauseSymbols(String clause) {
    if (!clause.startsWith("{")) {
      return List.of();
    }
    return parseImportClauseSymbols(clause);
  }

  private static void parseBarrelExportMappings(
      String clause, String resolvedTargetModule, Map<String, String> barrelSymbolToModule) {
    int openBrace = clause.indexOf('{');
    int closeBrace = clause.lastIndexOf('}');
    if (openBrace == -1 || closeBrace <= openBrace) {
      return;
    }
    String body = clause.substring(openBrace + 1, closeBrace);
    for (String raw : body.split(",")) {
      String cleaned = raw.trim().replaceFirst("^type\\s+", "").trim();
      if (cleaned.isEmpty()) continue;
      String[] parts = cleaned.split("\\s+as\\s+");
      String exportedName = parts.length > 1 ? parts[1].trim() : parts[0].trim();
      if (exportedName.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
        barrelSymbolToModule.put(exportedName, resolvedTargetModule);
      }
    }
  }

  private static String extractOriginalSymbol(String rawToken) {
    String cleaned = rawToken.trim().replaceFirst("^type\\s+", "").trim();
    if (cleaned.isEmpty() || cleaned.startsWith("*")) {
      return null;
    }
    String[] parts = cleaned.split("\\s+as\\s+");
    String orig = parts[0].trim();
    if ("default".equals(orig) && parts.length > 1) {
      orig = parts[1].trim();
    }
    return orig.matches("[a-zA-Z_$][a-zA-Z0-9_$]*") ? orig : null;
  }

  private static List<String> parseParameters(String rawParams) {
    if (rawParams == null || rawParams.isBlank()) {
      return List.of();
    }
    List<String> params = new ArrayList<>();
    for (String part : splitTopLevelCommas(rawParams)) {
      String cleaned =
          part.trim().replaceFirst("^(?:public|private|protected|readonly)\\s+", "").trim();
      if (!cleaned.isEmpty()) {
        params.add(cleaned);
      }
    }
    return params;
  }

  private static List<String> splitTopLevelCommas(String input) {
    List<String> result = new ArrayList<>();
    int angleDepth = 0;
    int parenDepth = 0;
    int braceDepth = 0;
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '<') angleDepth++;
      else if (c == '>' && angleDepth > 0) angleDepth--;
      else if (c == '(') parenDepth++;
      else if (c == ')' && parenDepth > 0) parenDepth--;
      else if (c == '{') braceDepth++;
      else if (c == '}' && braceDepth > 0) braceDepth--;
      else if (c == ',' && angleDepth == 0 && parenDepth == 0 && braceDepth == 0) {
        result.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      result.add(current.toString());
    }
    return result;
  }

  private static String stripGenerics(String typeName) {
    int idx = typeName.indexOf('<');
    return idx > 0 ? typeName.substring(0, idx).trim() : typeName;
  }

  private Map<String, String> loadTsConfigPathAliases(Path rootPath, String srcRelativePath) {
    Map<String, String> aliases = new LinkedHashMap<>();
    String effectiveSrc =
        (srcRelativePath != null && !srcRelativePath.isBlank()) ? srcRelativePath : "src";
    effectiveSrc = effectiveSrc.replace('\\', '/').replaceAll("^/+|/+$", "");

    for (String cfgName : List.of("tsconfig.json", "jsconfig.json")) {
      Path cfgPath = rootPath.resolve(cfgName);
      if (!Files.exists(cfgPath)) {
        continue;
      }
      try {
        List<String> lines = Files.readAllLines(cfgPath, StandardCharsets.UTF_8);
        String cleanJson = String.join("\n", extractLogicalTsStatements(lines));
        Matcher pathsBlockMatcher =
            Pattern.compile("\"paths\"\\s*:\\s*\\{([\\s\\S]*?)\\}").matcher(cleanJson);
        if (pathsBlockMatcher.find()) {
          String pathsBody = pathsBlockMatcher.group(1);
          Matcher entryMatcher =
              Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").matcher(pathsBody);
          while (entryMatcher.find()) {
            String key = entryMatcher.group(1).trim();
            String target = entryMatcher.group(2).trim().replace('\\', '/');
            if (key.endsWith("*")) {
              key = key.substring(0, key.length() - 1);
            }
            if (target.endsWith("*")) {
              target = target.substring(0, target.length() - 1);
            }
            target = target.replaceFirst("^\\./+", "");
            if (!effectiveSrc.isEmpty() && target.startsWith(effectiveSrc + "/")) {
              target = target.substring(effectiveSrc.length() + 1);
            }
            aliases.put(key, target);
          }
        }
      } catch (IOException ignored) {
        // Ignore unreadable tsconfig
      }
    }
    return aliases;
  }

  private String toTsModuleId(String relPath) {
    String mod = relPath.replace('\\', '/');
    if (mod.startsWith("src/")) {
      mod = mod.substring(4);
    }
    mod = mod.replaceAll("\\.(svelte\\.ts|svelte\\.js|ts|tsx|js|jsx|svelte)$", "");
    if (mod.endsWith("/index")) {
      mod = mod.substring(0, mod.length() - 6);
    }
    return mod.replace('/', '.');
  }

  private String getDirectoryModule(String relPath, String currentModule) {
    String norm = relPath.replace('\\', '/');
    boolean isIndex =
        norm.matches(".*(?:/|^)index\\.(?:svelte\\.ts|svelte\\.js|ts|tsx|js|jsx|svelte)$");
    if (isIndex) {
      return "index".equals(currentModule) ? "" : currentModule;
    }
    int idx = currentModule.lastIndexOf('.');
    return idx > 0 ? currentModule.substring(0, idx) : "";
  }

  private String getPackageName(String moduleId) {
    int idx = moduleId.lastIndexOf('.');
    return idx > 0 ? moduleId.substring(0, idx) : moduleId;
  }

  private String resolveTsImport(
      String directoryModule, String importPath, Map<String, String> pathAliases) {
    String resolved = importPath;
    if (pathAliases != null && !pathAliases.isEmpty()) {
      for (Map.Entry<String, String> entry : pathAliases.entrySet()) {
        String aliasPrefix = entry.getKey();
        if (importPath.startsWith(aliasPrefix)) {
          String mapped = entry.getValue() + importPath.substring(aliasPrefix.length());
          return stripKnownExtension(normalizePath(mapped)).replace('/', '.');
        }
      }
    }
    if (importPath.startsWith(".")) {
      String baseDir = directoryModule.replace('.', '/');
      String combined = baseDir.isEmpty() ? importPath : baseDir + "/" + importPath;
      return stripKnownExtension(normalizePath(combined)).replace('/', '.');
    }
    if (importPath.startsWith("@/") || importPath.startsWith("~/") || importPath.startsWith("#/")) {
      return stripKnownExtension(importPath.substring(2)).replace('/', '.');
    }
    if (importPath.startsWith("#lib/") || importPath.startsWith("$lib/")) {
      return "lib." + importPath.substring(5).replace('/', '.');
    }
    if (importPath.startsWith("@workspace/")) {
      return importPath.substring(11).replace('/', '.');
    }
    return resolved;
  }

  private String stripKnownExtension(String path) {
    String stripped = path.replaceAll("\\.(ts|tsx|js|jsx)$", "");
    if (stripped.endsWith("/index")) {
      stripped = stripped.substring(0, stripped.length() - 6);
    }
    return stripped;
  }

  private String normalizePath(String path) {
    String[] parts = path.split("/+");
    List<String> result = new ArrayList<>();
    for (String p : parts) {
      if (p.equals(".") || p.isEmpty()) continue;
      if (p.equals("..")) {
        if (!result.isEmpty()) result.remove(result.size() - 1);
      } else {
        result.add(p);
      }
    }
    return String.join("/", result);
  }

  private List<Path> discoverTypeScriptFiles(
      Path scanPath, Path rootPath, Set<String> omitPatterns) {
    return FileScannerUtil.findFiles(
        scanPath,
        p -> {
          String s = p.toString().replace('\\', '/');
          if (s.endsWith(".d.ts")) {
            return false;
          }
          if (LanguageScanner.matchesOmitPattern(rootPath.relativize(p).toString(), omitPatterns)) {
            return false;
          }
          return s.endsWith(".ts")
              || s.endsWith(".tsx")
              || s.endsWith(".js")
              || s.endsWith(".jsx")
              || s.endsWith(".svelte");
        });
  }
}
