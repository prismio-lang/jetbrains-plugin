# Graph Report - intellij-plugin  (2026-10-08)

## Corpus Check
- 157 files · ~76,183 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 14 file(s) not represented in the graph (top: .xml 5, .properties 3, (none) 2)

## Summary
- 1903 nodes · 5444 edges · 109 communities (31 shown, 78 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 213 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `bc422fe0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Tok
- Declaration
- CompilerSelector
- TypeInference
- com.intellij.openapi.util.TextRange
- PrismioFormatterTest
- PrismioTypeHintsProvider.java
- UmsLexer
- PrismioSettingsConfigurable
- UmsStructureViewFactory.java
- PrismioCompletionContributor.java
- UmsContext
- com.intellij.openapi.editor.Editor
- com.intellij.psi.PsiElement
- PrismioNameReference
- PrismioScopeResolver
- PrismioBlock.java
- PrismioExternalAnnotator
- PrismioToolchainService
- PrismioColorSettingsPage
- PrismioSmartCompletionTest
- PrismioRunConfiguration
- UmsRunConfigurations
- SignatureParser
- com.intellij.psi.PsiFile
- PrismioSpellcheckingStrategy.java
- PrismioLexer
- PrismioNewProjectWizard.java
- PrismioBraceMatcher.java
- notnull
- PrismioDocRenderDefault.java
- PrismioIcons.java
- path
- org.jetbrains.annotations.NotNull
- PrismioProjectSettings
- UmsManifest
- PrismioParameterInfoHandler.java
- Lookups.java
- CreateUmsManifestAction.java
- UmsFileType
- ImportPaths
- PrismioDirectoryProjectGenerator
- PrismioFile.java
- Prismio Language Support
- PrismioAdvancedFeaturesTest
- PrismioConfigurationFactory.java
- CompilerIntrinsics
- .getGotoDeclarationTargets
- AddImportFix
- PrismioCodeInsightTest.java
- PrismioTokenType
- UmsRunConfigurationsTest
- TypeRef
- impl String
- FnSig
- PrismioGotoDeclarationTest
- CreatePrismioFileAction.java
- UmsQuoteHandler
- .arguments
- PrismioColorSettingsPage.java
- PrismioSyntaxHighlighter.java
- PrismioDocumentationProvider
- com.intellij.lang.Language
- PrismioFindUsagesProvider
- TypeDecl
- Display
- PrismioExternalAnnotator.java
- Plugin Marketplace Icon
- com.intellij.openapi.project.Project
- PrismioChooseByNameContributor.java
- process.psm
- math.psm
- com.intellij.psi.tree.IElementType
- DeclaredHostTest
- DeclarationKind
- PrismioEnterHandler.java
- org.jetbrains.annotations.Nullable
- PrismioSettingsConfigurable.java
- PrismioAdvancedFeaturesTest.java
- .receiverBefore
- PrismioProjectStepTest
- com.intellij.testFramework.fixtures.BasePlatformTestCase
- com.intellij.openapi.editor.highlighter.HighlighterIterator
- com.intellij.codeInsight.completion.CompletionResultSet
- UmsValueChecksTest
- ModuleSummary
- UmsRunConfigurationProducer.java
- PrismioProjectStep.java
- UmsContextTest.java
- PrismioFileType
- PrismioAstFactory.java
- PrismioLineBreakpointType

## God Nodes (most connected - your core abstractions)
1. `PrismioLexer` - 67 edges
2. `TypeRef` - 47 edges
3. `SignatureParser` - 42 edges
4. `TypeInference` - 41 edges
5. `PrismioSmartCompletionTest` - 40 edges
6. `PrismioRunConfiguration` - 36 edges
7. `FnSig` - 31 edges
8. `UmsContext` - 30 edges
9. `PrismioTypes` - 27 edges
10. `Tok` - 27 edges

## Surprising Connections (you probably didn't know these)
- `Miniature Standard Library Test Mock` --conceptually_related_to--> `Code Completion & Live Templates`  [INFERRED]
  src/test/testData/stdlib/README.md → README.md
- `Shown` --references--> `FnSig`  [EXTRACTED]
  src/main/java/io/prismio/completion/PrismioParameterInfoHandler.java → src/main/java/io/prismio/symbols/FnSig.java
- `PrismioRunConfigurationEditor` --references--> `PrismioRunConfiguration`  [EXTRACTED]
  src/main/java/io/prismio/execution/PrismioRunConfigurationEditor.java → src/main/java/io/prismio/execution/PrismioRunConfiguration.java
- `PrismioRunConfigurationProducer` --references--> `PrismioRunConfiguration`  [EXTRACTED]
  src/main/java/io/prismio/execution/PrismioRunConfigurationProducer.java → src/main/java/io/prismio/execution/PrismioRunConfiguration.java
- `UmsRunConfigurationProducer` --references--> `PrismioRunConfiguration`  [EXTRACTED]
  src/main/java/io/prismio/execution/UmsRunConfigurationProducer.java → src/main/java/io/prismio/execution/PrismioRunConfiguration.java

## Import Cycles
- None detected.

## Communities (109 total, 78 thin omitted)

### Community 0 - "Tok"
Cohesion: 0.14
Nodes (11): CompletionPosition, Kind, EXPRESSION, IMPORT, IMPORT_FROM, MEMBER, NONE, STATEMENT (+3 more)

### Community 2 - "CompilerSelector"
Cohesion: 0.19
Nodes (5): CompilerSelector, Status, ERROR, NEUTRAL, OK

### Community 4 - "TypeInference"
Cohesion: 0.18
Nodes (3): Import, TypeInference, Value

### Community 5 - "com.intellij.openapi.util.TextRange"
Cohesion: 0.07
Nodes (6): PrismioInlineDocumentation, PrismioInlineDocumentationProvider, PrismioBlockCommentPostFormatProcessor, PrismioIdentifier, PrismioIdentifierManipulator, PrismioReferenceTest

### Community 7 - "PrismioTypeHintsProvider.java"
Cohesion: 0.13
Nodes (3): PrismioFormattingModelBuilder, PrismioTypeHintsProvider, UmsFormattingModelBuilder

### Community 10 - "UmsStructureViewFactory.java"
Cohesion: 0.07
Nodes (6): PrismioStructureViewElement, PrismioStructureViewFactory, PrismioStructureViewModel, Entry, Node, UmsStructureViewFactory

### Community 12 - "UmsContext"
Cohesion: 0.08
Nodes (13): UmsAnnotator, Role, ARGS, BLOCK, CALL, KEY, VALUE, Token (+5 more)

### Community 13 - "com.intellij.openapi.editor.Editor"
Cohesion: 0.14
Nodes (5): PrismioTypedHandler, Result, Err, Ok, impl Result

### Community 16 - "PrismioScopeResolver"
Cohesion: 0.26
Nodes (3): LocalDecl, PrismioScopeResolver, ScopeInfo

### Community 17 - "PrismioBlock.java"
Cohesion: 0.05
Nodes (9): PrismioBlock, PrismioParser, PrismioParserDefinition, PrismioCodeStyleSettings, PrismioCodeStylePanel, PrismioCodeStyleSettingsProvider, UmsBlock, UmsParser (+1 more)

### Community 18 - "PrismioExternalAnnotator"
Cohesion: 0.08
Nodes (9): PrismioCheckTarget, PrismioDiagnostic, PrismioDiagnosticParser, AnnotationResult, Checked, InitialInfo, PrismioExternalAnnotator, PrismioCheckTargetTest (+1 more)

### Community 24 - "UmsRunConfigurations"
Cohesion: 0.27
Nodes (3): Located, Spec, UmsRunConfigurations

### Community 27 - "com.intellij.psi.PsiFile"
Cohesion: 0.16
Nodes (3): PrismioCompletionContributor, ProjectSymbols, Model

### Community 28 - "PrismioSpellcheckingStrategy.java"
Cohesion: 0.21
Nodes (4): CommentTokenizer, PrismioSpellcheckingStrategy, StringTokenizer, UmsSpellcheckingStrategy

### Community 29 - "PrismioLexer"
Cohesion: 0.06
Nodes (3): PrismioLexer, PrismioCodeInsightTest, PrismioLexerTest

### Community 33 - "PrismioDocRenderDefault.java"
Cohesion: 0.17
Nodes (3): PrismioDocRenderDefault, Startup, PrismioDocRenderDefaultTest

### Community 34 - "PrismioIcons.java"
Cohesion: 0.22
Nodes (3): PrismioRunConfigurationType, PrismioIcons, Prismio File Icon

### Community 38 - "UmsManifest"
Cohesion: 0.20
Nodes (5): UmsRunLineMarkerContributor, Command, Target, Tok, UmsManifest

### Community 39 - "PrismioParameterInfoHandler.java"
Cohesion: 0.25
Nodes (3): PrismioParameterInfoHandler, Shown, Call

### Community 40 - "Lookups.java"
Cohesion: 0.14
Nodes (7): CallInsertHandler, Entry, FreestandingBuiltins, ArgumentsInsertHandler, AssignmentInsertHandler, BlockInsertHandler, UmsCompletionContributor

### Community 44 - "PrismioDirectoryProjectGenerator"
Cohesion: 0.15
Nodes (3): Peer, PrismioDirectoryProjectGenerator, Settings

### Community 46 - "PrismioFile.java"
Cohesion: 0.13
Nodes (4): PrismioLanguage, PrismioFile, PrismioTokenSets, UmsFile

### Community 47 - "Prismio Language Support"
Cohesion: 0.18
Nodes (12): Contributing Guide, Development Setup & Gradle Guide, JFlex Lexer Guidelines, Code Completion & Live Templates, Formatting & Indentation, Code Navigation & Structure View, Prismio Language Support, Syntax Highlighting & Color Settings (+4 more)

### Community 50 - "CompilerIntrinsics"
Cohesion: 0.22
Nodes (3): CompilerIntrinsics, Entry, CompilerIntrinsicsTest

### Community 57 - "impl String"
Cohesion: 0.14
Nodes (3): impl String, impl StringBuilder, StringBuilder

### Community 59 - "FnSig"
Cohesion: 0.16
Nodes (9): MemberTargets, FnSig, Member, Origin, BUILT_IN, EXTENSION, OWN, TRAIT (+1 more)

### Community 75 - "TypeDecl"
Cohesion: 0.15
Nodes (12): Kind, CONSTANT, FIELD, METHOD, PROPERTY, STATIC_METHOD, VARIANT, Kind (+4 more)

### Community 76 - "Display"
Cohesion: 0.38
Nodes (3): impl Display for Int, impl Display for String, Display

### Community 82 - "process.psm"
Cohesion: 0.40
Nodes (3): impl ProcessQuery, ProcessQuery, process

### Community 87 - "DeclarationKind"
Cohesion: 0.14
Nodes (11): DeclarationKind, CONSTANT, ENUM, ENUM_VARIANT, FIELD, FUNCTION, IMPLEMENTATION, PARAMETER (+3 more)

### Community 102 - "ModuleSummary"
Cohesion: 0.18
Nodes (5): Global, ModuleSummary, Snapshot, SymbolTable, StdlibCorpusTest

## Knowledge Gaps
- **52 isolated node(s):** `NONE`, `TOP_LEVEL`, `STATEMENT`, `EXPRESSION`, `TYPE` (+47 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 295 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **78 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PrismioLexer` connect `PrismioLexer` to `Tok`, `Declaration`, `PrismioSyntaxHighlighter.java`, `path`, `com.intellij.openapi.util.TextRange`, `PrismioFindUsagesProvider`, `PrismioFile.java`, `com.intellij.openapi.project.Project`, `PrismioScopeResolver`, `PrismioBlock.java`, `PrismioCodeInsightTest.java`, `com.intellij.psi.tree.IElementType`, `PrismioEnterHandler.java`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **Why does `PrismioSmartCompletionTest` connect `PrismioSmartCompletionTest` to `com.intellij.testFramework.fixtures.BasePlatformTestCase`, `PrismioProjectSettings`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **Why does `PrismioFormatterTest` connect `PrismioFormatterTest` to `com.intellij.testFramework.fixtures.BasePlatformTestCase`, `PrismioLexer`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **What connects `NONE`, `TOP_LEVEL`, `STATEMENT` to the rest of the system?**
  _52 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Tok` be split into smaller, more focused modules?**
  _Cohesion score 0.1402116402116402 - nodes in this community are weakly interconnected._
- **Should `PrismioDirectoryProjectGenerator.java` be split into smaller, more focused modules?**
  _Cohesion score 0.10333333333333333 - nodes in this community are weakly interconnected._
- **Should `com.intellij.openapi.util.TextRange` be split into smaller, more focused modules?**
  _Cohesion score 0.06748911465892599 - nodes in this community are weakly interconnected._