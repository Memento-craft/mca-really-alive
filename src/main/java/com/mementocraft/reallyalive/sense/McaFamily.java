package com.mementocraft.reallyalive.sense;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * A villager's family as MCA's family tree knows it: its parents, partner, children, siblings and
 * grandparents, each a family word and a first name ({"mother", "Rosa"}). MCA is reached by
 * reflection, so without it (or if it changes) a villager simply has no family to tell of.
 */
final class McaFamily {
    private static final String TREE = "net.conczin.mca.server.world.data.FamilyTree";
    private static Method get;
    private static Method lookup;
    private static boolean missing;

    private McaFamily() {}

    static List<String[]> of(Villager v) {
        List<String[]> family = new ArrayList<>();
        if (missing || !(v.level() instanceof ServerLevel level)) {
            return family;
        }
        try {
            if (get == null) {
                Class<?> tree = Class.forName(TREE);
                get = tree.getMethod("get", ServerLevel.class);
                lookup = tree.getMethod("getOrEmpty", UUID.class);
            }
            Object tree = get.invoke(null, level);
            Object self = node(tree, v.getUUID());
            if (self == null) {
                return family;
            }
            for (String parent : new String[] {"mother", "father"}) {
                Object p = node(tree, (UUID) call(self, parent));
                if (p != null) {
                    add(family, p, "mother", "father", null);
                    for (String grand : new String[] {"mother", "father"}) {
                        add(family, node(tree, (UUID) call(p, grand)), "grandmother", "grandfather", null);
                    }
                }
            }
            add(family, node(tree, (UUID) call(self, "partner")), "wife", "husband", null);
            for (String kind : new String[] {"children", "siblings"}) {
                for (Object id : (Collection<?>) call(self, kind)) {
                    Object n = node(tree, (UUID) id);
                    if (kind.equals("children")) {
                        add(family, n, "daughter", "son", "child");
                    } else {
                        add(family, n, "sister", "brother", null);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            missing = true;
            family.clear();
        }
        return family;
    }

    private static Object node(Object tree, UUID id) throws ReflectiveOperationException {
        if (id == null || id.equals(Util.NIL_UUID)) {
            return null;
        }
        return ((Optional<?>) lookup.invoke(tree, id)).orElse(null);
    }

    private static Object call(Object o, String method) throws ReflectiveOperationException {
        return o.getClass().getMethod(method).invoke(o);
    }

    /** Adds a relative by the word for a woman or a man, or the plain word if neither is known. */
    private static void add(List<String[]> family, Object n, String woman, String man, String plain)
            throws ReflectiveOperationException {
        if (n == null) {
            return;
        }
        String name = (String) call(n, "getName");
        String gender = String.valueOf(call(n, "gender"));
        String word = gender.equals("FEMALE") ? woman : gender.equals("MALE") ? man : plain;
        if (word != null && name != null && !name.isBlank()) {
            family.add(new String[] {word, name.trim().split("\\s+")[0]});
        }
    }
}
