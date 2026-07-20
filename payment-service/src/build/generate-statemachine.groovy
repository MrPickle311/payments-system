/**
 * PUML-to-Spring-StateMachine code generator.
 *
 * Reads a PlantUML state diagram and emits a StateMachineConfigurerAdapter Java class.
 *
 * Convention (parsed from PUML transition labels):
 *   - Event name:       first token after ':'
 *   - Guard:            [guardName]
 *   - Negated guard:    [guardName → description]  (fires when guard returns false)
 *   - Action:           / actionName
 *   - Error action:     (err: errorActionName)
 *   - Entry action:     STATE : entry / actionName  (inside a state body)
 *   - Region separator: -- (inside a composite state)
 *   - Notes:            note on link ... end note   (skipped)
 */

// ---------------------------------------------------------------------------
// 1. Read properties passed from gmavenplus-plugin
// ---------------------------------------------------------------------------

def pumlFile    = new File(pumlFile as String)
def outputDir   = new File((outputDir as String).replace("src/main/java", "target/generated-sources/statemachine"))
def packageName = packageName as String
def stateEnum   = stateEnum as String
def eventEnum   = eventEnum as String

def stateEnumSimple = stateEnum.substring(stateEnum.lastIndexOf('.') + 1)
def eventEnumSimple = eventEnum.substring(eventEnum.lastIndexOf('.') + 1)

println "[StateMachine Generator] Reading ${pumlFile.absolutePath}"

if (!pumlFile.exists()) {
    throw new RuntimeException("PUML file not found: ${pumlFile.absolutePath}")
}

// ---------------------------------------------------------------------------
// 2. Data model
// ---------------------------------------------------------------------------

class TransitionDef {
    String source
    String target
    String event
    String guard
    boolean guardNegated = false
    String action
    String errorAction
}

class RegionDef {
    String name
    String initialState
    List<String> states = []
}

String initialState = null
Set<String> allStates = new LinkedHashSet<>()
List<TransitionDef> transitions = []
Map<String, String> entryActions = [:]       // state -> action bean name
// compositeParent -> list of regions (each region has its own states)
Map<String, List<RegionDef>> compositeRegions = [:]
// Set of names that are region labels (not real states)
Set<String> regionLabels = new LinkedHashSet<>()

// ---------------------------------------------------------------------------
// 3. Parse PUML
// ---------------------------------------------------------------------------

def lines = pumlFile.readLines()

// Pre-processing: skip notes, skinparam blocks, title, @startuml/@enduml
boolean inNote = false
boolean inSkinparam = false
List<String> cleaned = []

for (line in lines) {
    def trimmed = line.trim()

    if (trimmed.startsWith('note on link') || trimmed.startsWith('note ')) {
        inNote = true; continue
    }
    if (inNote) {
        if (trimmed == 'end note') inNote = false
        continue
    }
    if (trimmed.startsWith('skinparam')) {
        inSkinparam = true; continue
    }
    if (inSkinparam) {
        if (trimmed == '}') inSkinparam = false
        continue
    }
    if (trimmed.startsWith('@startuml') || trimmed.startsWith('@enduml') ||
        trimmed.startsWith('title ') || trimmed.isEmpty()) {
        continue
    }
    cleaned << line
}

// ---------------------------------------------------------------------------
// Parse transition label into components
// ---------------------------------------------------------------------------
def parseLabel = { String label ->
    def result = [event: null, guard: null, guardNegated: false,
                  action: null, errorAction: null]

    // Split on literal \n (PlantUML line break in labels)
    def parts = label.split('\\\\n')

    for (part in parts) {
        def p = part.trim()

        // Guard: [guardName] or [guardName → description]
        def guardMatch = (p =~ /\[([^\]]+)\]/)
        if (guardMatch.find()) {
            def guardContent = guardMatch.group(1).trim()
            if (guardContent.contains('→') || guardContent.contains('->')) {
                result.guard = guardContent.split(/\s*[→\-\>]+\s*/)[0].trim()
                result.guardNegated = true
            } else {
                result.guard = guardContent
            }
            continue
        }

        // Error action: (err: actionName)
        def errMatch = (p =~ /\(err:\s*(\w+)\)/)
        if (errMatch.find()) {
            result.errorAction = errMatch.group(1)
            continue
        }

        // Skip documentation annotations like (30-day window)
        if (p.startsWith('(') && p.endsWith(')') && !p.contains('err:')) {
            continue
        }

        // Action: / actionName
        def actionMatch = (p =~ /^\/\s*(\w+)/)
        if (actionMatch.find()) {
            result.action = actionMatch.group(1)
            continue
        }

        // Event: first unmatched token (must be the event name)
        if (result.event == null && p ==~ /^[A-Z_]+$/) {
            result.event = p
        }
    }

    return result
}

// ---------------------------------------------------------------------------
// Recursive parser
//
// The PUML nesting looks like:
//   state PROCESSING {          <-- composite with orthogonal regions
//     state Authorization {     <-- named region (NOT a real state)
//       [*] --> AUTH_PENDING
//       AUTH_PENDING --> AUTH_APPROVED : ...
//     }
//     --                        <-- region separator
//     state FraudCheck {        <-- second named region
//       [*] --> FRAUD_EVALUATING
//     }
//   }
//
// The parser tracks:
//   - compositeStack: stack of parent composite names
//   - When a named sub-state is opened inside a composite that has '--',
//     it is treated as a region label rather than a state.
// ---------------------------------------------------------------------------

// First pass: identify composites that have '--' (orthogonal regions)
Set<String> compositesWithRegions = new LinkedHashSet<>()
List<String> tempStack = []
for (line in cleaned) {
    def trimmed = line.trim()
    def cm = (trimmed =~ /^state\s+(\w+)\s*\{/)
    if (cm.find()) {
        tempStack << cm.group(1)
    } else if (trimmed == '}' && !tempStack.isEmpty()) {
        tempStack.removeLast()
    } else if (trimmed == '--' && !tempStack.isEmpty()) {
        compositesWithRegions << tempStack.last()
    }
}

// Second pass: full parse
List<String> compositeStack = []
String currentParent = null       // the outermost composite with regions
String currentRegionLabel = null  // e.g. "Authorization", "FraudCheck"
int regionIndex = -1

int i = 0
while (i < cleaned.size()) {
    def line = cleaned[i].trim()

    // Composite/sub-state declaration: state NAME {
    def compositeMatch = (line =~ /^state\s+(\w+)\s*\{/)
    if (compositeMatch.find()) {
        def name = compositeMatch.group(1)

        if (compositesWithRegions.contains(name)) {
            // This is a top-level composite with orthogonal regions (e.g. PROCESSING)
            compositeStack << name
            currentParent = name
            allStates << name
            compositeRegions[name] = []
            // Start implicit first region
            regionIndex = 0
            compositeRegions[name] << new RegionDef(name: "${name}_region_${regionIndex}")
            i++
            continue
        }

        if (currentParent != null) {
            // Named sub-state inside a composite-with-regions → this is a region label
            regionLabels << name
            currentRegionLabel = name
            // Update the current region's name to this label
            if (regionIndex >= 0 && regionIndex < compositeRegions[currentParent].size()) {
                compositeRegions[currentParent][regionIndex].name = name
            }
            compositeStack << name
            i++
            continue
        }

        // A composite without '--' (e.g. COMPLETED with just an entry action)
        // Treat it as a normal state; don't create regions
        compositeStack << name
        allStates << name
        i++
        continue
    }

    // Region separator: --
    if (line == '--' && currentParent != null) {
        regionIndex++
        compositeRegions[currentParent] << new RegionDef(
            name: "${currentParent}_region_${regionIndex}")
        i++
        continue
    }

    // End of block: }
    if (line == '}') {
        if (!compositeStack.isEmpty()) {
            def closed = compositeStack.removeLast()
            if (closed == currentParent) {
                currentParent = null
                regionIndex = -1
                currentRegionLabel = null
            } else if (regionLabels.contains(closed)) {
                currentRegionLabel = null
            }
        }
        i++
        continue
    }

    // Initial state: [*] --> STATE
    def initialMatch = (line =~ /\[\*\]\s*-->\s*(\w+)/)
    if (initialMatch.find()) {
        def state = initialMatch.group(1)
        allStates << state
        if (currentParent != null && regionIndex >= 0) {
            // Inside a region of a composite
            compositeRegions[currentParent][regionIndex].initialState = state
            compositeRegions[currentParent][regionIndex].states << state
        } else {
            initialState = state
        }
        i++
        continue
    }

    // Entry action: STATE : entry / actionName
    def entryMatch = (line =~ /^(\w+)\s*:\s*entry\s*\/\s*(\w+)/)
    if (entryMatch.find()) {
        def state = entryMatch.group(1)
        def actionName = entryMatch.group(2)
        entryActions[state] = actionName
        i++
        continue
    }

    // Transition: SOURCE --> TARGET : LABEL
    def transitionMatch = (line =~ /^(\w+)\s*-->\s*(\w+)\s*:\s*(.+)$/)
    if (transitionMatch.find()) {
        def source = transitionMatch.group(1)
        def target = transitionMatch.group(2)
        def label  = transitionMatch.group(3).trim()

        allStates << source
        allStates << target

        // Add states to current region if inside a composite
        if (currentParent != null && regionIndex >= 0) {
            def region = compositeRegions[currentParent][regionIndex]
            if (!region.states.contains(source)) region.states << source
            if (!region.states.contains(target)) region.states << target
        }

        def parsed = parseLabel(label)

        transitions << new TransitionDef(
            source: source,
            target: target,
            event: parsed.event,
            guard: parsed.guard,
            guardNegated: parsed.guardNegated,
            action: parsed.action,
            errorAction: parsed.errorAction
        )
        i++
        continue
    }

    // Transition without label: SOURCE --> TARGET
    def transitionNoLabelMatch = (line =~ /^(\w+)\s*-->\s*(\w+)\s*$/)
    if (transitionNoLabelMatch.find()) {
        def source = transitionNoLabelMatch.group(1)
        def target = transitionNoLabelMatch.group(2)
        allStates << source
        allStates << target
        transitions << new TransitionDef(source: source, target: target)
        i++
        continue
    }

    i++
}

// Remove region labels from allStates — they are not real enum values
allStates.removeAll(regionLabels)

// ---------------------------------------------------------------------------
// 4. Collect all referenced guards and actions
// ---------------------------------------------------------------------------

Set<String> guardNames = new LinkedHashSet<>()
Set<String> actionNames = new LinkedHashSet<>()

for (t in transitions) {
    if (t.guard) guardNames << t.guard
    if (t.action) actionNames << t.action
    if (t.errorAction) actionNames << t.errorAction
}
for (entry in entryActions.values()) {
    actionNames << entry
}

// Determine top-level states (not inside any composite region)
Set<String> regionStates = new LinkedHashSet<>()
for (entry in compositeRegions) {
    for (region in entry.value) {
        regionStates.addAll(region.states)
    }
}
def topLevelStates = allStates.findAll { !regionStates.contains(it) }

println "[StateMachine Generator] Parsed:"
println "  Initial state:    ${initialState}"
println "  All states:       ${allStates}"
println "  Top-level states: ${topLevelStates}"
println "  Region labels:    ${regionLabels}"
println "  Composites:       ${compositeRegions.keySet()}"
for (entry in compositeRegions) {
    for (region in entry.value) {
        println "    ${entry.key} / ${region.name}: initial=${region.initialState}, states=${region.states}"
    }
}
println "  Transitions:      ${transitions.size()}"
println "  Guards:           ${guardNames}"
println "  Actions:          ${actionNames}"
println "  Entry actions:    ${entryActions}"

// ---------------------------------------------------------------------------
// 5. Generate Java source
// ---------------------------------------------------------------------------

def className = 'GeneratedStateMachineConfig'
def packageDir = new File(outputDir, packageName.replace('.', '/'))
packageDir.mkdirs()

def outputFile = new File(packageDir, "${className}.java")

def sb = new StringBuilder()

// --- Header ---
sb << """package ${packageName};

import ${stateEnum};
import ${eventEnum};
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

/**
 * Auto-generated from {@code statemachine.puml}.
 *
 * <p>DO NOT EDIT — regenerate by running {@code mvn generate-sources}.
 *
 * <p>This abstract base class defines the state machine topology (states,
 * transitions, regions). Subclass it and implement the abstract guard/action
 * methods to provide the business logic.
 *
 * @see StateMachineConfig
 */
public abstract class ${className}
        extends StateMachineConfigurerAdapter<${stateEnumSimple}, ${eventEnumSimple}> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(${className}.class);
"""

// --- Abstract guard methods ---
if (!guardNames.isEmpty()) {
    sb << """    // =========================================================================
    // Guards — implement in subclass
    // =========================================================================

"""
    for (g in guardNames) {
        sb << "    protected abstract Guard<${stateEnumSimple}, ${eventEnumSimple}> ${g}();\n\n"
    }
}

// --- Abstract action methods ---
if (!actionNames.isEmpty()) {
    sb << """    // =========================================================================
    // Actions — implement in subclass (Standard blocking code)
    // =========================================================================

"""
    for (a in actionNames) {
        sb << "    protected abstract void execute${a.capitalize()}(org.springframework.statemachine.StateContext<${stateEnumSimple}, ${eventEnumSimple}> context) throws Exception;\n\n"
    }

    sb << """    // =========================================================================
    // Reactive Wrappers (Auto-generated)
    // =========================================================================

    private final java.util.concurrent.ExecutorService virtualThreadExecutor =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    protected void sendFailureEvent(org.springframework.statemachine.StateContext<${stateEnumSimple}, ${eventEnumSimple}> context, Exception e) {
        log.error("Unhandled exception in state machine action, transitioning to FAILED", e);
        Long paymentId = context.getExtendedState().get("PAYMENT_ID", Long.class);
        if (paymentId == null) return;
        com.example.payment.application.saga.SagaContextProxy.sendEventWithRetries(context.getStateMachine(), ${eventEnumSimple}.FAIL, paymentId);
    }

"""
    for (a in actionNames) {
        sb << """    protected org.springframework.statemachine.action.ReactiveAction<${stateEnumSimple}, ${eventEnumSimple}> ${a}() {
        return context -> reactor.core.publisher.Mono.fromRunnable(() -> {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    execute${a.capitalize()}(context);
                } catch (Exception e) {
                    sendFailureEvent(context, e);
                }
            }, virtualThreadExecutor);
        }).then();
    }

"""
    }
}

// --- Global configuration ---
sb << """    // =========================================================================
    // Global machine configuration
    // =========================================================================

    @Override
    public void configure(StateMachineConfigurationConfigurer<${stateEnumSimple}, ${eventEnumSimple}> config)
            throws Exception {
        config.withConfiguration()
                .regionExecutionPolicy(org.springframework.statemachine.region.RegionExecutionPolicy.PARALLEL)
                .autoStartup(false);
    }

"""

// --- State configuration ---
sb << """    // =========================================================================
    // State registry (generated from PUML)
    // =========================================================================

    @Override
    public void configure(StateMachineStateConfigurer<${stateEnumSimple}, ${eventEnumSimple}> states)
            throws Exception {
        states
            .withStates()
                .initial(${stateEnumSimple}.${initialState})
"""

// Top-level states (excluding initial)
for (s in topLevelStates) {
    if (s == initialState) {
        if (entryActions.containsKey(s)) {
            sb << "                .stateEntryFunction(${stateEnumSimple}.${s}, ${entryActions[s]}())\n"
        }
        continue
    }
    if (entryActions.containsKey(s)) {
        sb << "                .state(${stateEnumSimple}.${s})\n"
        sb << "                .stateEntryFunction(${stateEnumSimple}.${s}, ${entryActions[s]}())\n"
    } else {
        sb << "                .state(${stateEnumSimple}.${s})\n"
    }
}

// Regions inside composites
for (compEntry in compositeRegions) {
    def parentName = compEntry.key
    def regions = compEntry.value
    for (region in regions) {
        sb << """                .and()
            // Region: ${region.name} (parent: ${parentName})
            .withStates()
                .parent(${stateEnumSimple}.${parentName})
"""
        if (region.initialState) {
            sb << "                .initial(${stateEnumSimple}.${region.initialState})\n"
        }
        for (rs in region.states) {
            if (rs == region.initialState) {
                if (entryActions.containsKey(rs)) {
                    sb << "                .stateEntryFunction(${stateEnumSimple}.${rs}, ${entryActions[rs]}())\n"
                }
                continue
            }
            if (entryActions.containsKey(rs)) {
                sb << "                .state(${stateEnumSimple}.${rs})\n"
                sb << "                .stateEntryFunction(${stateEnumSimple}.${rs}, ${entryActions[rs]}())\n"
            } else {
                sb << "                .state(${stateEnumSimple}.${rs})\n"
            }
        }
    }
}

// Close the state configuration
sb << """                ;
    }

"""

// --- Transition configuration ---
sb << """    // =========================================================================
    // Transition table (generated from PUML)
    // =========================================================================

    @Override
    public void configure(StateMachineTransitionConfigurer<${stateEnumSimple}, ${eventEnumSimple}> transitions)
            throws Exception {
        transitions
"""

for (int t = 0; t < transitions.size(); t++) {
    def tr = transitions[t]
    def isLast = (t == transitions.size() - 1)

    sb << "            .withExternal()\n"
    sb << "                .source(${stateEnumSimple}.${tr.source}).target(${stateEnumSimple}.${tr.target})\n"

    if (tr.event) {
        sb << "                .event(${eventEnumSimple}.${tr.event})\n"
    }
    if (tr.guard) {
        if (tr.guardNegated) {
            sb << "                .guard(negate(${tr.guard}()))\n"
        } else {
            sb << "                .guard(${tr.guard}())\n"
        }
    }
    if (tr.action) {
        if (tr.errorAction) {
            sb << "                .actionFunction(ctx -> ${tr.action}().apply(ctx).onErrorResume(err -> ${tr.errorAction}().apply(ctx).then(reactor.core.publisher.Mono.error(err))))\n"
        } else {
            sb << "                .actionFunction(${tr.action}())\n"
        }
    }

    if (!isLast) {
        sb << "                .and()\n"
    }
}

sb << """                ;
    }

"""

// --- Negate helper (only if we have negated guards) ---
boolean hasNegated = transitions.any { it.guardNegated }
if (hasNegated) {
    sb << """    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns a guard that negates the given guard.
     * Used for transitions that fire when the original guard returns {@code false}.
     */
    private Guard<${stateEnumSimple}, ${eventEnumSimple}> negate(
            Guard<${stateEnumSimple}, ${eventEnumSimple}> guard) {
        return context -> !guard.evaluate(context);
    }

"""
}

sb << '}\n'

// ---------------------------------------------------------------------------
// 6. Write output
// ---------------------------------------------------------------------------

outputFile.text = sb.toString()
println "[StateMachine Generator] Generated ${outputFile.absolutePath}"

