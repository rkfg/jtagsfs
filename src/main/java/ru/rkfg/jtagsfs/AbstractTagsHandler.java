package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import net.fusejna.ErrorCodes;

import org.hibernate.Session;

import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.domain.Tag;

public abstract class AbstractTagsHandler extends UnsupportedFSHandler {

    protected void addTag(final String newTag) {
        addTag(newTag, null);
    }

    protected void addTag(final String newTag, final String parent) {
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                Tag tag = getTagByName(newTag, session);
                if (tag == null) {
                    tag = new Tag(newTag);
                    if (parent != null) {
                        Tag parentTag = getTagByName(parent, session);
                        tag.setParent(parentTag);
                    }
                    session.save(tag);
                }
                return null;
            }
        });
    }

    protected void addTagForFilepath(Filepath filepath) throws FSHandlerException {
        List<String> tags = Arrays.asList(filepath.getPath());
        if (tags.size() == 0 || tags.contains(ENDOFTAGS)) {
            throw new FSHandlerException("invaliddir");
        }
        if (filepath.getPathLength() > 1) {
            addTag(filepath.getPathLast(), filepath.getPath()[filepath.getPathLength() - 2]);
        } else {
            addTag(filepath.getPathLast());
        }
    }

    protected int deleteTag(final String delTag) {
        try {
            HibernateUtil.exec(new HibernateCallback<Void>() {

                public Void run(Session session) {
                    Tag tag = getTagByName(delTag, session);
                    if (tag == null) {
                        throw new RuntimeException();
                    }
                    session.delete(tag);
                    return null;
                }
            });
        } catch (RuntimeException e) {
            return -ErrorCodes.ENOENT();
        }
        return 0;
    }

    protected Tag getTagByName(String tagName, Session session) {
        return (Tag) session.createQuery("from Tag t where t.name = :tag").setString("tag", tagName).uniqueResult();
    }

    protected List<String> getTags(final String[] exclude) {
        return getTags(exclude, true);
    }

    protected List<String> getTags(final String[] exclude, final boolean strict) {
        return HibernateUtil.exec(new HibernateCallback<List<String>>() {

            @SuppressWarnings("unchecked")
            public List<String> run(Session session) {
                LinkedList<String> result = new LinkedList<String>();
                List<Tag> tags;
                HashMap<String, Object> params = new HashMap<String, Object>();
                String query = "select t from Tag t left join t.parent p where 1=1";
                if (exclude != null && exclude.length > 0) {
                    query += " and t.name not in (:exc)";
                    params.put("exc", exclude);
                }
                if (strict) {
                    if (exclude.length > 0) {
                        query += " and t.parent.name = :p";
                        params.put("p", exclude[exclude.length - 1]);
                    } else {
                        query += " and t.parent is null";
                    }
                } else {
                    query += " and ((t.parent is not null and p.name in (:p)) or t.parent is null)";
                    params.put("p", exclude);
                }
                tags = session.createQuery(query).setProperties(params).list();
                for (Tag tag : tags) {
                    result.add(tag.getName());
                }
                return result;
            }
        });
    }

    protected void renameTag(final Filepath from, final Filepath to, Session session) {
        String fromTagName = from.getPathLast();
        String toTagName = to.getPathLast();
        Tag fromTag = getTagByName(fromTagName, session);
        fromTag.setName(toTagName);
        if (to.getPathLength() > 1) {
            String toParentTagName = to.getPath()[to.getPathLength() - 2];
            Tag parentTag = getTagByName(toParentTagName, session);
            fromTag.setParent(parentTag);
        } else {
            fromTag.setParent(null);
        }
    }

}
