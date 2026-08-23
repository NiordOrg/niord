package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants that are still prose, each with the task that will make it real.
 *
 * This file exists so that "not yet asserted" is a DECLARED state with an owner
 * rather than an absence. The manifest test rejects a pending with no owner and
 * a pending naming a task that does not exist, and prints the count on every run
 * -- so the number of rules still living as prose is visible rather than inferred.
 *
 * Named ...Test so Surefire actually runs it. It was PendingInvariants first,
 * which looks exactly like a test class and is silently ignored -- the class
 * matters less than the annotations on it, but a file that appears to assert
 * things and never executes is its own small trap.
 *
 * B1.7b is the task that drives this file to empty. It cannot run until the
 * transactional actions exist, because most of what is left here is about what
 * happens at freeze, at publish, and at cutover.
 */
public class PendingInvariantsTest {

    /** 4 rule(s) awaiting B2.12. */
    @BindsRule(value = {"D-3", "D-5", "D-6", "D-8"}, pending = "B2.12")
    @Test
    public void awaitingB2_12() {
        assertTrue(true, "declared pending on B2.12");
    }

    /** 1 rule(s) awaiting B2.2. */
    @BindsRule(value = {"X-6"}, pending = "B2.2")
    @Test
    public void awaitingB2_2() {
        assertTrue(true, "declared pending on B2.2");
    }

    /** 6 rule(s) awaiting B2.3b. */
    @BindsRule(value = {"I-7", "I-8", "I-9", "I-13", "I-16", "X-7"}, pending = "B2.3b")
    @Test
    public void awaitingB2_3b() {
        assertTrue(true, "declared pending on B2.3b");
    }

    /** 1 rule(s) awaiting B2.6. */
    @BindsRule(value = {"I-15"}, pending = "B2.6")
    @Test
    public void awaitingB2_6() {
        assertTrue(true, "declared pending on B2.6");
    }

    /** 3 rule(s) awaiting B2.7. */
    @BindsRule(value = {"I-14", "I-18", "I-19"}, pending = "B2.7")
    @Test
    public void awaitingB2_7() {
        assertTrue(true, "declared pending on B2.7");
    }

    /** 1 rule(s) awaiting B2.8. */
    @BindsRule(value = {"X-5"}, pending = "B2.8")
    @Test
    public void awaitingB2_8() {
        assertTrue(true, "declared pending on B2.8");
    }

    /** 7 rule(s) awaiting B2.9. */
    @BindsRule(value = {"O-1", "O-2", "O-3", "O-4", "O-5", "O-6", "O-7"}, pending = "B2.9")
    @Test
    public void awaitingB2_9() {
        assertTrue(true, "declared pending on B2.9");
    }

    /** 1 rule(s) awaiting B3.4a. */
    @BindsRule(value = {"RI-14"}, pending = "B3.4a")
    @Test
    public void awaitingB3_4a() {
        assertTrue(true, "declared pending on B3.4a");
    }

    /** 1 rule(s) awaiting B4.5. */
    @BindsRule(value = {"RI-15"}, pending = "B4.5")
    @Test
    public void awaitingB4_5() {
        assertTrue(true, "declared pending on B4.5");
    }

    /** 4 rule(s) awaiting B4.7. */
    @BindsRule(value = {"X-1", "X-2", "X-3", "X-4"}, pending = "B4.7")
    @Test
    public void awaitingB4_7() {
        assertTrue(true, "declared pending on B4.7");
    }

    /** 1 rule(s) awaiting B5.4a. */
    @BindsRule(value = {"D-4"}, pending = "B5.4a")
    @Test
    public void awaitingB5_4a() {
        assertTrue(true, "declared pending on B5.4a");
    }

}
