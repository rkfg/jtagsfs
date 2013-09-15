package ru.rkfg.jtagsfs;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.fusejna.StructStat.StatWrapper;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.VirtualDirectory.VirtualEntryNotFound;

public class RootHandler extends UnsupportedFSHandler {

    List<FSHandler> handlers = new LinkedList<FSHandler>();
    VirtualDirectory virtualDirectory = new VirtualDirectory();

    public RootHandler() {
        handlers.addAll(Arrays.asList(new StatHandler(), new ControlHandler(), new TagsHandler()));
        for (FSHandler handler : handlers) {
            virtualDirectory.add(new VirtualEntry(handler.getPrefix(), EntryType.DIR, 4096));
        }
    }

    @Override
    public String getPrefix() {
        return "/";
    }

    @Override
    public List<String> readdir(Filepath filepath) {
        return virtualDirectory.list();
    }

    @Override
    public void getattr(Filepath filepath, StatWrapper stat) throws FSHandlerException {
        try {
            if (filepath.getPath().length == 0) {
                stat.mode(VirtualEntry.DIRMODE);
                stat.size(4096);
                return;
            }
            if (filepath.getPath().length == 1) {
                VirtualEntry entry = virtualDirectory.getEntryByName(filepath.getPathLast());
                if (entry != null) {
                    entry.setStat(stat);
                    return;
                }
            }
        } catch (VirtualEntryNotFound e) {
        }
        throw new FSHandlerException("notfound");
    }

    public List<FSHandler> getHandlers() {
        return handlers;
    }

    @Override
    public int getDepth() {
        return 0;
    }

    @Override
    public int read(Filepath filepath, ByteBuffer buffer, long size, long offset) throws FSHandlerException {
        return 0;
    }

}
