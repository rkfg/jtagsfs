package ru.rkfg.jtagsfs;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

public class HibernateUtil {

    private static SessionFactory sessionFactory = null;

    public static <T> T exec(HibernateCallback<T> callback) {
        T result = null;
        if (sessionFactory != null) {
            Session session = sessionFactory.openSession();
            try {
                session.beginTransaction();
                result = callback.run(session);
                if (session.getTransaction().isActive()) {
                    session.getTransaction().commit();
                }
            } catch (HibernateException e) {
                session.getTransaction().rollback();
                throw e;
            } finally {
                session.close();
            }
        }
        return result;
    }

    public static void initSessionFactory(String cfgFilename) {
        ServiceRegistry serviceRegistry;
        try {
            // Create the SessionFactory from hibernate.cfg.xml
            Configuration configuration = new Configuration();
            configuration.configure(cfgFilename);
            serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();
            sessionFactory = configuration.buildSessionFactory(serviceRegistry);
        } catch (Throwable ex) {
            // Make sure you log the exception, as it might be swallowed
            System.err.println("Initial SessionFactory creation failed: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static Session getSession() {
        return sessionFactory.openSession();
    }

    public static void cleanup() {
        sessionFactory.close();
        sessionFactory = null;
    }
}
