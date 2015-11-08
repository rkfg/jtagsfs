package ru.rkfg.jtagsfs;

import java.util.Set;

import net.fusejna.StructStat.StatWrapper;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.VirtualDirectory.VirtualEntryNotFound;

public class StatHandler extends UnsupportedFSHandler {

    VirtualDirectory virtualDirectory = new VirtualDirectory();
    VirtualEntry tagsEntry = new VirtualEntry("tagscount", EntryType.FILE, 0);
    VirtualEntry readmeEntry = new VirtualEntry("readme.txt", EntryType.FILE, 10);

    public StatHandler() {
        virtualDirectory.add(tagsEntry, readmeEntry);
    }

    @Override
    public String getPrefix() {
        return "stat";
    }

    @Override
    public Set<String> readdir(Filepath filepath) {
        return virtualDirectory.list();
    }

    @Override
    public void getattr(Filepath filepath, StatWrapper stat) throws FSHandlerException {
        try {
            VirtualEntry entry = virtualDirectory.getEntryByName(filepath.getPathLast());
            if (entry != null) {
                entry.setStat(stat);
            }
        } catch (VirtualEntryNotFound e) {
            throw new FSHandlerException("notfound");
        }
    }

    @Override
    public int getDepth() {
        return 1;
    }

}
