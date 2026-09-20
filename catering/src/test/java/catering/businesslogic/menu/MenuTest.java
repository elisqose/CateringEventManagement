package catering.businesslogic.menu;

import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuTest {

    private User chef;
    private Menu menu;

    @BeforeEach
    void setUp() {
        chef = new User("chef");
        chef.setId(1);
        chef.addRole(User.Role.CHEF);
        menu = new Menu(chef, "Test Menu");
    }

    @Test
    void testIsOwner_SameUser_ReturnsTrue() {
        assertTrue(menu.isOwner(chef));
    }

    @Test
    void testIsOwner_DifferentUser_ReturnsFalse() {
        User other = new User("other");
        other.setId(2);
        assertFalse(menu.isOwner(other));
    }

    @Test
    void testAddSection_AppendsSection() {
        Section s = menu.addSection("Antipasti");
        assertEquals("Antipasti", s.getName());
        assertEquals(1, menu.getSectionCount());
        assertTrue(menu.hasSection(s));
    }

    @Test
    void testHasSection_NotInMenu_ReturnsFalse() {
        Section orphan = new Section("Orphan");
        assertFalse(menu.hasSection(orphan));
    }

    @Test
    void testAddItem_WithSection_GoesIntoSection() {
        Section s = menu.addSection("Antipasti");
        Recipe r = new Recipe("Bruschetta");
        MenuItem mi = menu.addItem(r, s, "House bruschetta");

        assertEquals(1, s.getItems().size());
        assertEquals(0, menu.getFreeItems().size());
        assertEquals(s, menu.getSection(mi));
    }

    @Test
    void testAddItem_NullSection_GoesToFreeItems() {
        Recipe r = new Recipe("Olives");
        MenuItem mi = menu.addItem(r, null, "Olives");

        assertEquals(1, menu.getFreeItems().size());
        assertNull(menu.getSection(mi));
    }

    @Test
    void testAddItem_NullDescription_StoredAsNull() {
        Recipe r = new Recipe("Bread");
        MenuItem mi = menu.addItem(r, null, null);
        assertNull(mi.getDescription());
        assertEquals(r, mi.getRecipe());
    }

    @Test
    void testDeepCopy_ProducesFreshMenuItemInstances() {
        Section s = menu.addSection("Antipasti");
        Recipe r = new Recipe("Bruschetta");
        MenuItem original = menu.addItem(r, s, "Tomato bruschetta");

        Menu copy = menu.deepCopy();

        assertEquals(menu.getSectionCount(), copy.getSectionCount());
        MenuItem copiedItem = copy.getSections().get(0).getItems().get(0);
        assertNotSame(original, copiedItem, "deepCopy must produce a fresh MenuItem instance");
        assertEquals(original.getDescription(), copiedItem.getDescription());
        assertSame(original.getRecipe(), copiedItem.getRecipe(),
                "Recipe is shared by design: deepCopy clones the menu structure, not the recipe book");
    }

    @Test
    void testGetSectionCount_EmptyMenu_ReturnsZero() {
        assertEquals(0, menu.getSectionCount());
    }

    @Test
    void testIsInUse_FreshMenu_ReturnsFalse() {
        assertFalse(menu.isInUse());
    }
}
