package com.example.payment.domain.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.domain.enums.PaymentState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if {@link SagaRegion} and {@code statemachine.puml} disagree. Compares state sets,
 * not region names — PlantUML {@code --} regions are anonymous, so a name is just whoever read the
 * file's choice, but which states belong to which region is exactly what the join depends on.
 */
class SagaRegionPumlConsistencyTest {

    private static final Path PUML = Path.of("src/main/resources/statemachine.puml");

    private static final Pattern STATE_BLOCK = Pattern.compile("^\\s*state\\s+(\\w+)\\s*\\{\\s*$");
    private static final Pattern REGION_SEPARATOR = Pattern.compile("^\\s*--\\s*$");
    private static final Pattern UPPER_SNAKE_TOKEN = Pattern.compile("\\b([A-Z][A-Z0-9_]{2,})\\b");

    @Test
    @DisplayName("each composite's regions in the .puml match SagaRegion, state for state")
    void regionsMatchThePuml() throws IOException {
        Map<PaymentState, List<Set<PaymentState>>> pumlRegions = parseRegions();

        assertThat(pumlRegions)
                .as("parser found no regions at all - the .puml format probably changed")
                .isNotEmpty();

        for (Map.Entry<PaymentState, List<Set<PaymentState>>> entry : pumlRegions.entrySet()) {
            PaymentState composite = entry.getKey();
            List<Set<PaymentState>> fromPuml = entry.getValue();

            List<Set<PaymentState>> declared = SagaRegion.forComposite(composite).stream()
                    .map(SagaRegion::allStates)
                    .map(states -> (Set<PaymentState>) new LinkedHashSet<>(states))
                    .toList();

            assertThat(declared)
                    .as(
                            "SagaRegion declares %d region(s) for %s but statemachine.puml has %d. "
                                    + "Add or remove the matching SagaRegion constant.",
                            declared.size(), composite, fromPuml.size())
                    .hasSameSizeAs(fromPuml);

            assertThat(declared)
                    .as(
                            "SagaRegion's regions for %s must cover exactly the states the .puml puts in "
                                    + "each region. A state missing here is a state the join cannot see.",
                            composite)
                    .containsExactlyInAnyOrderElementsOf(fromPuml);
        }
    }

    @Test
    @DisplayName("every composite with regions in the .puml has a SagaJoin")
    void everyRegionedCompositeHasAJoin() throws IOException {
        for (PaymentState composite : parseRegions().keySet()) {
            assertThat(SagaJoin.forComposite(composite))
                    .as(
                            "%s has parallel regions in the .puml but no SagaJoin, so nothing would ever "
                                    + "emit its join event",
                            composite)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("every region has at least one terminal state, or joins on it could never complete")
    void everyRegionCanTerminate() {
        for (SagaRegion region : SagaRegion.values()) {
            assertThat(region.successTerminalStates().size()
                            + region.failureTerminalStates().size())
                    .as("region %s has no terminal states, so a join over it would hang forever", region)
                    .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("no state is claimed by two regions")
    void regionsDoNotOverlap() {
        Map<PaymentState, SagaRegion> seen = new LinkedHashMap<>();
        for (SagaRegion region : SagaRegion.values()) {
            for (PaymentState state : region.allStates()) {
                SagaRegion previous = seen.put(state, region);
                assertThat(previous)
                        .as("state %s is claimed by both %s and %s", state, previous, region)
                        .isNull();
            }
        }
    }

    @Test
    @DisplayName("region labels are unique - payment_history.region must be unambiguous")
    void regionLabelsAreUnique() {
        Set<String> labels = Arrays.stream(SagaRegion.values())
                .map(SagaRegion::getLabel)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertThat(labels).hasSameSizeAs(Arrays.asList(SagaRegion.values()));
    }

    /** Composite -&gt; list of region state-sets. Composites with no nested regions (entry-action-only) are skipped. */
    private static Map<PaymentState, List<Set<PaymentState>>> parseRegions() throws IOException {
        List<String> lines = Files.readAllLines(PUML, StandardCharsets.UTF_8);
        Map<PaymentState, List<Set<PaymentState>>> result = new LinkedHashMap<>();

        String composite = null;
        int depth = 0;
        Set<PaymentState> current = new LinkedHashSet<>();

        for (String raw : lines) {
            String line = stripComment(raw);
            Matcher blockStart = STATE_BLOCK.matcher(line);

            if (blockStart.matches()) {
                String name = blockStart.group(1);
                depth++;
                if (depth == 1) {
                    composite = name;
                    current = new LinkedHashSet<>();
                } else if (depth == 2) {
                    // Named sub-block: a region. Its own states start fresh.
                    current = new LinkedHashSet<>();
                }
                continue;
            }

            if (line.trim().equals("}")) {
                if (depth == 2 || depth == 1) {
                    closeRegion(result, composite, current);
                    current = new LinkedHashSet<>();
                }
                if (depth > 0) {
                    depth--;
                }
                if (depth == 0) {
                    composite = null;
                }
                continue;
            }

            if (REGION_SEPARATOR.matcher(line).matches()) {
                closeRegion(result, composite, current);
                current = new LinkedHashSet<>();
                continue;
            }

            if (depth >= 1 && composite != null) {
                current.addAll(statesMentionedIn(line, composite));
            }
        }
        return result;
    }

    private static void closeRegion(
            Map<PaymentState, List<Set<PaymentState>>> result, String composite, Set<PaymentState> states) {
        if (composite == null || states.isEmpty()) {
            return;
        }
        result.computeIfAbsent(PaymentState.valueOf(composite), key -> new ArrayList<>())
                .add(new LinkedHashSet<>(states));
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('\'');
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    /** PaymentState tokens on a transition or entry-action line, excluding the composite itself. */
    private static Set<PaymentState> statesMentionedIn(String line, String composite) {
        Set<PaymentState> found = new LinkedHashSet<>();
        Matcher tokens = UPPER_SNAKE_TOKEN.matcher(line);
        while (tokens.find()) {
            String token = tokens.group(1);
            if (token.equals(composite)) {
                continue;
            }
            toState(token).ifPresent(found::add);
        }
        return found;
    }

    private static java.util.Optional<PaymentState> toState(String token) {
        try {
            return java.util.Optional.of(PaymentState.valueOf(token));
        } catch (IllegalArgumentException notAState) {
            return java.util.Optional.empty();
        }
    }
}
