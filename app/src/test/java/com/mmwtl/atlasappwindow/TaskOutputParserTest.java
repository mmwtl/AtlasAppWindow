package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class TaskOutputParserTest {
    private static final WindowBounds EXPECTED = new WindowBounds(24, 600, 696, 1450);

    @Test
    public void findsAndVerifiesExactAndroid11Task() {
        String dump = ""
                + "  * Task{8ca #42 visible=true type=standard mode=freeform "
                + "A=10123:com.maps U=0 StackId=7 sz=1}\n"
                + "    mBounds=Rect(24, 600 - 696, 1450)\n"
                + "    realActivity=com.maps/.LauncherActivity\n"
                + "    * Hist #0: ActivityRecord{abc u0 com.maps/.MapActivity t42}\n";

        assertEquals(42, TaskOutputParser.taskIdForComponent(
                dump, "com.maps/com.maps.LauncherActivity"));
        assertEquals(TaskOutputParser.Verification.VERIFIED,
                TaskOutputParser.verifyTask(
                        dump, 42, "com.maps/.LauncherActivity", EXPECTED));
        assertTrue(TaskOutputParser.reportsFreeform(dump, 42));
    }

    @Test
    public void neverBorrowsModeOrBoundsFromAnotherTask() {
        String dump = ""
                + "  * Task{aaa #10 mode=fullscreen A=1000:com.target U=0}\n"
                + "    mBounds=Rect(0, 0 - 1440, 1920)\n"
                + "    realActivity=com.target/.MainActivity\n"
                + "  * Task{bbb #99 mode=freeform A=1001:com.other U=0}\n"
                + "    mBounds=Rect(24, 600 - 696, 1450)\n"
                + "    realActivity=com.other/.MainActivity\n";

        assertFalse(TaskOutputParser.reportsFreeform(dump, 10));
        assertEquals(TaskOutputParser.Verification.WRONG_MODE,
                TaskOutputParser.verifyTask(
                        dump, 10, "com.target/.MainActivity", EXPECTED));
    }

    @Test
    public void stackPreambleDoesNotContaminatePreviousOemTask() {
        String dump = ""
                + "  Stack #12: type=standard mode=freeform\n"
                + "    mBounds=Rect(24, 600 - 696, 1450)\n"
                + "    * Task{aaa #12 mode=freeform A=1000:com.target U=0}\n"
                + "      mBounds=Rect(24, 600 - 696, 1450)\n"
                + "      realActivity=com.target/.MainActivity\n"
                + "  Stack #10: type=standard mode=fullscreen\n"
                + "    mBounds=Rect(0, 0 - 1440, 1920)\n"
                + "    * Task{bbb #10 mode=fullscreen A=1001:com.other U=0}\n"
                + "      mBounds=Rect(0, 0 - 1440, 1920)\n"
                + "      realActivity=com.other/.MainActivity\n";

        assertTrue(TaskOutputParser.reportsFreeform(dump, 12));
        assertEquals(TaskOutputParser.Verification.VERIFIED,
                TaskOutputParser.verifyTask(
                        dump, 12, "com.target/.MainActivity", EXPECTED));
    }

    @Test
    public void exactComponentSelectionFailsClosedWhenAmbiguous() {
        String dump = ""
                + "  * Task{aaa #11 mode=freeform A=1000:com.target U=0}\n"
                + "    bounds=[24,600][696,1450]\n"
                + "    realActivity=com.target/.MainActivity\n"
                + "  * Task{bbb #12 mode=freeform A=1000:com.target U=0}\n"
                + "    bounds=[24,600][696,1450]\n"
                + "    realActivity=com.target/.MainActivity\n";

        assertEquals(-1, TaskOutputParser.taskIdForComponent(
                dump, "com.target/.MainActivity"));
        assertEquals(Set.of(11, 12), TaskOutputParser.taskIdsForComponent(
                dump, "com.target/.MainActivity"));
        assertEquals(Set.of(11, 12), TaskOutputParser.verifiedTaskIdsForComponent(
                dump, "com.target/.MainActivity", EXPECTED));
    }

    @Test
    public void uniquelySelectsVerifiedGeometryAmongDuplicateComponents() {
        String dump = ""
                + "  * Task{aaa #11 mode=fullscreen A=1000:com.target U=0}\n"
                + "    bounds=[0,0][1440,1920]\n"
                + "    realActivity=com.target/.MainActivity\n"
                + "  * Task{bbb #12 mode=freeform A=1000:com.target U=0}\n"
                + "    bounds=[24,600][696,1450]\n"
                + "    realActivity=com.target/.MainActivity\n";

        assertEquals(Set.of(11, 12), TaskOutputParser.taskIdsForComponent(
                dump, "com.target/.MainActivity"));
        assertEquals(Set.of(12), TaskOutputParser.verifiedTaskIdsForComponent(
                dump, "com.target/.MainActivity", EXPECTED));
    }

    @Test
    public void uniquelySelectsFreeformTaskWithOemAdjustedBounds() {
        String dump = ""
                + "  * Task{aaa #11 mode=fullscreen A=1000:com.target U=0}\n"
                + "    bounds=[0,0][1440,1920]\n"
                + "    realActivity=com.target/.MainActivity\n"
                + "  * Task{bbb #12 mode=freeform A=1000:com.target U=0}\n"
                + "    bounds=[50,140][1390,1120]\n"
                + "    realActivity=com.target/.MainActivity\n";

        assertEquals(Set.of(), TaskOutputParser.verifiedTaskIdsForComponent(
                dump, "com.target/.MainActivity", EXPECTED));
        assertEquals(Set.of(12), TaskOutputParser.freeformTaskIdsForComponent(
                dump, "com.target/.MainActivity"));
    }

    @Test
    public void packageHeaderDoesNotProveExactComponentOwnership() {
        String dump = ""
                + "  * Task{aaa #15 mode=freeform A=1000:com.target U=0}\n"
                + "    mBounds=Rect(24, 600 - 696, 1450)\n"
                + "    realActivity=com.target/.DifferentActivity\n";

        assertEquals(-1, TaskOutputParser.taskIdForComponent(
                dump, "com.target/.MainActivity"));
        assertEquals(Set.of(15), TaskOutputParser.taskIdsForPackage(dump, "com.target"));
        assertFalse(TaskOutputParser.taskHasExactComponent(
                dump, 15, "com.target/.MainActivity"));
    }

    @Test
    public void distinguishesWrongBoundsFromIncompleteVendorOutput() {
        String wrong = ""
                + "Task id #20\n"
                + "  windowingMode=freeform\n"
                + "  bounds=[0,0 - 1440,1920]\n"
                + "  realActivity=com.target/.MainActivity\n"
                + "  topActivity=ComponentInfo{com.target/.MainActivity}\n";
        String incomplete = ""
                + "Task id #21\n"
                + "  realActivity=com.target/.MainActivity\n";

        assertEquals(TaskOutputParser.Verification.WRONG_BOUNDS,
                TaskOutputParser.verifyTask(
                        wrong, 20, "com.target/.MainActivity", EXPECTED));
        assertEquals(TaskOutputParser.Verification.INDETERMINATE,
                TaskOutputParser.verifyTask(
                        incomplete, 21, "com.target/.MainActivity", EXPECTED));
    }

    @Test
    public void parsesAmStackListTaskLines() {
        String output = ""
                + "Stack id=3 bounds=[24,600][696,1450] displayId=0 userId=0\n"
                + "  taskId=77: com.target/.MainActivity bounds=[24,600][696,1450] "
                + "userId=0 visible=true topActivity=ComponentInfo{com.target/.MainActivity}\n";

        assertTrue(TaskOutputParser.containsTask(output, 77));
        assertEquals(-1, TaskOutputParser.taskIdForComponent(
                output, "com.target/.MainActivity"));
        assertEquals(Set.of(77), TaskOutputParser.taskIdsForPackage(output, "com.target"));
    }

    @Test
    public void parsesLegacyTaskIdBlocksOnlyAsFallback() {
        String output = ""
                + "mTaskId=88\n"
                + "mWindowingMode=5\n"
                + "mBounds=Rect(24, 600 - 696, 1450)\n"
                + "realActivity=com.target/.MainActivity\n";

        assertEquals(TaskOutputParser.Verification.VERIFIED,
                TaskOutputParser.verifyTask(
                        output, 88, "com.target/.MainActivity", EXPECTED));
    }
}
