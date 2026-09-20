package catering.businesslogic.menu;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.recipe.Recipe;
import catering.persistence.PersistenceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the gestire-menu DSDs in
 * slides/class-14-16-progettazione/gestire-menu/dsd/.
 * <p>
 * Each {@link Nested} class corresponds to one DSD operation. Per the slide
 * directives in slides/class-18-25-codice/Lab SAS 21 Testing.pdf, every
 * test exercises either the happy path or one precondition violation
 * (ALT-block clause) using {@link org.junit.jupiter.api.Assertions#assertThrows}.
 */
class MenuManagerTest {

    private static CatERing app;

    @BeforeAll
    static void init() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
    }

    @BeforeEach
    void setUp() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Antonio"); // Antonio is a chef
        app.getMenuManager().setCurrentMenu(null);
    }

    @Nested
    class CreateMenu {

        @Test
        void testCreateMenu_AsChef_ReturnsMenu() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            assertNotNull(m);
            assertEquals("Pranzo", m.getTitle());
            assertSame(m, app.getMenuManager().getCurrentMenu());
        }

        @Test
        void testCreateMenu_NonChef_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Luca"); // Luca is a cook, not a chef
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().createMenu("Pranzo"));
        }
    }

    @Nested
    class DefineSection {

        @Test
        void testDefineSection_AppendsSection() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");
            Section s = app.getMenuManager().defineSection("Antipasti");
            assertEquals("Antipasti", s.getName());
            assertEquals(1, app.getMenuManager().getCurrentMenu().getSectionCount());
        }

        @Test
        void testDefineSection_NoCurrentMenu_ThrowsUseCaseLogicException() {
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().defineSection("Antipasti"));
        }
    }

    @Nested
    class InsertItem {

        private Recipe recipe;

        @BeforeEach
        void seedMenu() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");
            recipe = new Recipe("Bruschetta");
        }

        @Test
        void testInsertItem_IntoSection_GoesToSection() throws UseCaseLogicException {
            Section s = app.getMenuManager().defineSection("Antipasti");
            MenuItem mi = app.getMenuManager().insertItem(recipe, s, "House bruschetta");
            assertEquals("House bruschetta", mi.getDescription());
            assertEquals(1, s.getItems().size());
        }

        @Test
        void testInsertItem_NullSection_GoesToFreeItems() throws UseCaseLogicException {
            app.getMenuManager().insertItem(recipe, null, "Standalone");
            assertEquals(1, app.getMenuManager().getCurrentMenu().getFreeItems().size());
        }

        @Test
        void testInsertItem_RecipeOnly_DefaultsDescriptionToRecipeName() throws UseCaseLogicException {
            MenuItem mi = app.getMenuManager().insertItem(recipe);
            assertEquals(recipe.getName(), mi.getDescription());
        }

        @Test
        void testInsertItem_NoCurrentMenu_ThrowsUseCaseLogicException() {
            app.getMenuManager().setCurrentMenu(null);
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().insertItem(recipe, null, "x"));
        }

        @Test
        void testInsertItem_SectionNotInCurrentMenu_ThrowsUseCaseLogicException() {
            Section orphan = new Section("Orphan");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().insertItem(recipe, orphan, "x"));
        }
    }

    @Nested
    class MoveSection {

        @Test
        void testMoveSection_ValidPosition_Reorders() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");
            Section first = app.getMenuManager().defineSection("Antipasti");
            Section second = app.getMenuManager().defineSection("Primi");

            app.getMenuManager().moveSection(second, 0);

            assertEquals(second, app.getMenuManager().getCurrentMenu().getSections().get(0));
            assertEquals(first, app.getMenuManager().getCurrentMenu().getSections().get(1));
        }

        @Test
        void testMoveSection_OutOfRange_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");
            Section s = app.getMenuManager().defineSection("Antipasti");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().moveSection(s, 5));
        }

        @Test
        void testMoveSection_NoCurrentMenu_ThrowsUseCaseLogicException() {
            Section orphan = new Section("Orphan");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().moveSection(orphan, 0));
        }

        @Test
        void testMoveSection_SectionNotInCurrentMenu_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");
            Section orphan = new Section("Orphan");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().moveSection(orphan, 0));
        }
    }

    @Nested
    class ChooseMenu {

        @Test
        void testChooseMenu_OwnedMenu_BecomesCurrent() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getMenuManager().setCurrentMenu(null);

            app.getMenuManager().chooseMenu(m);

            assertSame(m, app.getMenuManager().getCurrentMenu());
        }

        @Test
        void testChooseMenu_NotOwner_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Chiara"); // Chiara is a chef but not the menu owner
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().chooseMenu(m));
        }

        @Test
        void testChooseMenu_NonChef_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Luca"); // Luca is a cook
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().chooseMenu(m));
        }
    }

    @Nested
    class ChooseMenuForCopy {

        @Test
        void testChooseMenuForCopy_OwnershipTransfersToCurrentChef() throws UseCaseLogicException {
            // Antonio creates the source...
            Menu source = app.getMenuManager().createMenu("Source");
            app.getMenuManager().defineSection("Antipasti");

            // ...then Chiara logs in and copies it: the copy must be owned by Chiara.
            app.getUserManager().fakeLogin("Chiara");
            Menu copy = app.getMenuManager().chooseMenuForCopy(source);

            assertNotSame(source, copy);
            assertEquals(source.getSectionCount(), copy.getSectionCount());
            assertTrue(copy.isOwner(app.getUserManager().getCurrentUser()),
                    "chooseMenuForCopy must transfer ownership to the current chef");
        }

        @Test
        void testChooseMenuForCopy_NonChef_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            Menu source = app.getMenuManager().createMenu("Source");
            app.getUserManager().fakeLogin("Luca");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().chooseMenuForCopy(source));
        }
    }

    @Nested
    class DeleteMenu {

        @Test
        void testDeleteMenu_OwnedAndNotInUse_ClearsCurrentMenu() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getMenuManager().deleteMenu(m);
            assertNull(app.getMenuManager().getCurrentMenu());
        }

        @Test
        void testDeleteMenu_NotOwner_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Chiara");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().deleteMenu(m));
        }

        @Test
        void testDeleteMenu_NonChef_ThrowsUseCaseLogicException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Luca");
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().deleteMenu(m));
        }

        @Test
        void testDeleteMenu_NonCurrentMenu_StillClearsCurrentMenu() throws UseCaseLogicException {
            // Per DSD 1a.1_deleteMenu, setCurrentMenu(null) is unconditional.
            // Even when the deleted menu is not the one currently selected,
            // currentMenu is cleared.
            Menu first = app.getMenuManager().createMenu("First");
            Menu second = app.getMenuManager().createMenu("Second");
            assertSame(second, app.getMenuManager().getCurrentMenu());

            app.getMenuManager().deleteMenu(first);

            assertNull(app.getMenuManager().getCurrentMenu());
        }
    }
}
