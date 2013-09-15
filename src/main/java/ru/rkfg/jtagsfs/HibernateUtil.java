package ru.rkfg.jtagsfs;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;

public class HibernateUtil {

    private static List<SessionFactory> sessionFactory = new ArrayList<SessionFactory>();

    public static <T> T exec(HibernateCallback<T> callback) {
        return exec(0, callback);
    }

    public static <T> T exec(int sessionNumber, HibernateCallback<T> callback) {
        T result = null;
        SessionFactory sessionFactory = HibernateUtil.getSessionFactory(sessionNumber);
        if (sessionFactory != null) {
            Session session = sessionFactory.openSession();
            try {
                session.beginTransaction();
                result = callback.run(session);
                session.getTransaction().commit();
            } catch (HibernateException e) {
                session.getTransaction().rollback();
                throw e;
            } finally {
                session.close();
            }
        }
        return result;
    }

    public static List<SessionFactory> getSessionFactories() {
        return sessionFactory;
    }

    public static SessionFactory getSessionFactory() {
        return getSessionFactory(0);
    }

    public static SessionFactory getSessionFactory(int nIndex) {
        if (nIndex >= 0 && nIndex < sessionFactory.size()) {
            return sessionFactory.get(nIndex);
        }
        return null;
    }

    public static void initSessionFactory(String cfgFilename) {
        ServiceRegistry serviceRegistry;
        try {
            // Create the SessionFactory from hibernate.cfg.xml
            Configuration configuration = new Configuration();
            configuration.configure(cfgFilename);
            serviceRegistry = new ServiceRegistryBuilder().applySettings(configuration.getProperties()).buildServiceRegistry();
            sessionFactory.add(configuration.buildSessionFactory(serviceRegistry));
        } catch (Throwable ex) {
            // Make sure you log the exception, as it might be swallowed
            System.err.println("Initial SessionFactory creation failed: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

}
