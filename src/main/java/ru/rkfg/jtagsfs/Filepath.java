package ru.rkfg.jtagsfs;

import java.io.File;
import java.util.Arrays;

public class Filepath implements Cloneable {
    boolean content;
    boolean contentWithTags;
    String[] path;
    String name;

    public boolean isContent() {
        return content;
    }

    public void setContent(boolean content) {
        this.content = content;
    }

    public boolean isContentWithTags() {
        return contentWithTags;
    }

    public boolean isTagPath() {
        String pathLast = getPathLast();
        return !content && !pathLast.equals(Consts.ENDOFTAGS) && !pathLast.equals(Consts.EXCLUDETAGS)
                && !pathLast.equals(Consts.CONCATTAGS) && !pathLast.equals(Consts.TAGGEDCONTENT);
    }

    public void setContentWithTags(boolean contentWithTags) {
        this.contentWithTags = contentWithTags;
    }

    public String[] getPath() {
        return path;
    }

    public void setPath(String[] path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Filepath up(int levels) {
        try {
            Filepath result = (Filepath) clone();
            if (levels >= path.length) {
                result.setPath(new String[0]);
            } else {
                result.setPath(Arrays.copyOf(path, path.length - levels));
            }
            return result;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Filepath strip(int levels) {
        try {
            Filepath result = (Filepath) clone();
            if (levels == 0) {
                return this;
            }
            if (levels >= path.length) {
                result.setPath(new String[0]);
            } else {
                result.setPath(Arrays.copyOfRange(path, levels, path.length));
            }
            return result;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;

    }

    @Override
    public String toString() {
        return "Filepath [path=" + Arrays.toString(path) + ", name=" + name + "]";
    }

    public String getPathLast() {
        if (path.length > 0) {
            return path[path.length - 1];
        }
        return "";
    }

    public int getPathLength() {
        return path.length;
    }

    public String asStringPath() {
        StringBuilder sb = new StringBuilder(path.length * (11));
        for (String component : path) {
            if (sb.length() > 0) {
                sb.append(File.separator);
            }
            sb.append(component);
        }
        if (name != null) {
            sb.append(File.separator).append(Consts.ENDOFTAGS).append(File.separator).append(name);
        }
        return sb.toString();
    }
}
