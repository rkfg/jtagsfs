package ru.rkfg.jtagsfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class VirtualDirectory {

    public class VirtualEntryNotFound extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = -4535856394521288178L;

    }

    private List<VirtualEntry> entries = new ArrayList<VirtualEntry>();

    public List<String> list() {
        List<String> result = new LinkedList<String>();
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
