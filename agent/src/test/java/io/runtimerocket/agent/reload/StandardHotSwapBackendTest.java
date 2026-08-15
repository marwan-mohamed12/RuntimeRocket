package io.runtimerocket.agent.reload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardHotSwapBackendTest {

    private final StandardHotSwapBackend backend = new StandardHotSwapBackend();
    private final ClassDeltaClassifier classifier = new ClassDeltaClassifier();

    @Test
    void methodBodyIsFull() {
        ClassDelta delta = classifier.classify(DeltaFixtures.bodyChange().before(), DeltaFixtures.bodyChange().after());
        assertEquals(Support.FULL, backend.assess(delta));
    }

    @Test
    void constantPoolOnlyIsFull() {
        ClassDelta delta =
                classifier.classify(DeltaFixtures.lineNumbersOnly().before(), DeltaFixtures.lineNumbersOnly().after());
        assertEquals(Support.FULL, backend.assess(delta));
    }

    @Test
    void newTypeIsFull() {
        ClassDelta delta = classifier.classify(null, DeltaFixtures.bodyChange().after());
        assertTrue(delta.kinds.contains(ChangeKind.NEW_TYPE));
        assertEquals(Support.FULL, backend.assess(delta));
    }

    @Test
    void addMethodIsUnsupported() {
        ClassDelta delta = classifier.classify(DeltaFixtures.addMethod().before(), DeltaFixtures.addMethod().after());
        assertEquals(Support.UNSUPPORTED, backend.assess(delta));
    }

    @Test
    void mixedBodyAndAddMethodIsPartial() {
        ClassDelta body = classifier.classify(DeltaFixtures.bodyChange().before(), DeltaFixtures.bodyChange().after());
        java.util.EnumSet<ChangeKind> kinds = java.util.EnumSet.copyOf(body.kinds);
        kinds.add(ChangeKind.ADD_METHOD);
        ClassDelta mixed = new ClassDelta(
                body.internalName,
                kinds,
                body.addedMethods,
                body.removedMethods,
                body.bodyChangedMethods,
                body.addedFields,
                body.removedFields,
                body.superclassChanged,
                body.interfacesChanged,
                body.nestHostChanged,
                body.permittedSubclassesChanged,
                body.recordComponentsChanged,
                body.enumConstantsChanged,
                body.anonymousIndexShiftLikely);
        assertEquals(Support.PARTIAL, backend.assess(mixed));
    }
}
