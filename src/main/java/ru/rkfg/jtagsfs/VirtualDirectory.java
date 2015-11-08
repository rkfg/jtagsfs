package ru.rkfg.jtagsfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VirtualDirectory {

    public class VirtualEntryNotFound extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = -4535856394521288178L;

    }

    private List<VirtualEntry> entries = new ArrayList<VirtualEntry>();

    public Set<String> list() {
        Set<String> result = new HashSet<String>();
        for (VirtualEntry entry : entries) {
            result.add(entry.getName());
        }
        return result;
    }

    public VirtualEntry getEntryByName(String name) throws VirtualEntryNotFound {
        for (VirtualEntry entry : entries) {
            if (entry.getName().equals(name)) {
                return entry;
            }
        }
        throw new VirtualEntryNotFound();
    }

    public void add(VirtualEntry... entry) {
        entries.addAll(Arrays.asList(entry));
    }
}
