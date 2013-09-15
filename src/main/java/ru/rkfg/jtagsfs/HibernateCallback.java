package ru.rkfg.jtagsfs;

import org.hibernate.Session;

public interface HibernateCallback<T> {
    T run(Session session);
}
