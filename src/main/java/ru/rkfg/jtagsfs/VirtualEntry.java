package ru.rkfg.jtagsfs;

import net.fusejna.StructStat.StatWrapper;

public class VirtualEntry {
    public static long FILEMODE = 0100644;
    public static long DIRMODE = 040755;

    String name;
    EntryType type;
    long size;

    public VirtualEntry(String name, EntryType type, long size) {
        super();
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntryType getType() {
        return type;
    }

    public void setType(EntryType type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VirtualEntry other = (VirtualEntry) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    public void setStat(StatWrapper stat) {
        stat.mode(type == EntryType.FILE ? FILEMODE : DIRMODE);
        stat.size(size);
    }

}
