package com.extrarawstyle.ae2us.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

final class PinyinSearch {

    private static final Logger LOG = LogManager.getLogger("AE2US");
    private static Method contains;
    private static boolean checked;

    private PinyinSearch() {
    }

    static boolean matches(final String name, final String query) {
        final String text = name.toLowerCase(Locale.ROOT);
        final String search = query.toLowerCase(Locale.ROOT);
        if (text.contains(search)) {
            return true;
        }
        if (!checked) {
            checked = true;
            try {
                contains = Class.forName("me.towdium.jecharacters.util.Match")
                        .getMethod("contains", String.class, CharSequence.class);
            } catch (final ClassNotFoundException ignored) {
                return false;
            } catch (final ReflectiveOperationException | LinkageError error) {
                LOG.warn("JECharacters pinyin search is unavailable", error);
                return false;
            }
        }
        if (contains == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(contains.invoke(null, text, search));
        } catch (final IllegalAccessException | InvocationTargetException | LinkageError error) {
            LOG.warn("JECharacters pinyin search failed", error);
            contains = null;
            return false;
        }
    }
}
