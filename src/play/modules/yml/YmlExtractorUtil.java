/**
 *  This file is part of LogiSima.
 *
 *  LogiSima is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  LogiSima is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with LogiSima.  If not, see <http://www.gnu.org/licenses/>.
 */
package play.modules.yml;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;
import javax.persistence.Lob;

import org.apache.log4j.Level;
import org.hibernate.Hibernate;
import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.proxy.HibernateProxy;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import play.Logger;
import play.Play;
import play.db.jpa.JPABase;
import play.db.jpa.Model;
import play.modules.yml.models.YmlObject;
import play.utils.Utils;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * Util class for logisima-yml module.
 * 
 * @author bsimard
 * 
 */
public class YmlExtractorUtil {

    private static final String TAB = "    ";

    /**
     * Method that generate the YLM file.
     * 
     * @param output
     * @param filename
     * @param myHash
     * @throws IOException
     */
    public static void writeYml(String output, String filename, HashMap<String, YmlObject> ymlObjects)
            throws IOException {
        // we create the file
        File file = new File(output + "/" + filename + ".yml");
        FileOutputStream fop = new FileOutputStream(file);
        fop.write("# Generated by logisima-play-yml (http://github.com/sim51/logisima-play-yml).\n".getBytes());
        fop.write("# This module is a part of LogiSima (http://www.logisima.com).\n".getBytes());
        Iterator it = ymlObjects.entrySet().iterator();
        String tmp = "";
        while (it.hasNext()) {
            Entry object = (Entry) it.next();
            YmlObject ymlObject = (YmlObject) object.getValue();
            if (!ymlObject.isAlreadyWrite()) {
                tmp = writeObject2Yml(ymlObjects, ymlObject);
                fop.write(tmp.getBytes());
            }

        }
        fop.flush();
        fop.close();
    }

    /**
     * Recursive method to write object.
     * 
     * @param objectMap
     * @param object
     * @return
     */
    public static String writeObject2Yml(HashMap<String, YmlObject> ymlObjects, YmlObject object) {
        String ymlText = "";
        if (!object.isAlreadyWrite()) {
            // we mark the object as write !
            object.setAlreadyWrite(Boolean.TRUE);
            ymlObjects.put(object.getId(), object);

            if (object.getChildren().size() != 0) {
                Logger.debug("NB of children for " + object.getId() + " is " + object.getChildren().size());
                for (int i = 0; i < object.getChildren().size(); i++) {
                    ymlText += writeObject2Yml(ymlObjects, ymlObjects.get(object.getChildren().get(i)));
                }
            }
            ymlText += object.getYmlValue();
        }
        return ymlText;
    }

    /**
     * Method to convert an object to YmlObject.
     * 
     * @param jpaSupport
     * @return
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @throws InvocationTargetException
     */
    public static YmlObject object2YmlObject(JPABase jpaBase) throws IllegalArgumentException, IllegalAccessException,
            ParseException, SecurityException, NoSuchMethodException, InvocationTargetException {
        // Init YAML
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);

        // Initialization of YmlObject
        YmlObject ymlObject = new YmlObject();
        ymlObject.setId(getObjectId(jpaBase));

        // String value for the object
        String stringObject = "\n" + getObjectClassName(jpaBase) + "(" + getObjectId(jpaBase) + "):\n";
        Logger.info("Generate YML for class id :" + getObjectId(jpaBase) + "(" + jpaBase.getClass().getFields().length
                + "fields)");

        if (jpaBase.getClass().getCanonicalName().contains("_$$_")) {
            Hibernate.initialize(jpaBase);
            HibernateProxy proxy = (HibernateProxy) jpaBase;
            jpaBase = (JPABase) proxy.getHibernateLazyInitializer().getImplementation();
        }

        for (java.lang.reflect.Field field : jpaBase.getClass().getFields()) {

            // map that will contain all object field
            Map<String, Object> data = new HashMap<String, Object>();

            String name = field.getName();

            if (!name.equals("id") && !name.equals("willBeSaved")) {

                Boolean valueIsSet = Boolean.FALSE;
                Logger.debug("Generated field " + name);

                if (field.get(jpaBase) != null) {

                    // if field is a List
                    if (List.class.isInstance(field.get(jpaBase))) {
                        Logger.debug("Field " + name + " type is List");
                        List myList = (List) field.get(jpaBase);
                        if (!myList.isEmpty() && myList.size() > 0) {
                            String[] tmpValues = new String[myList.size()];
                            for (int i = 0; i < myList.size(); i++) {
                                tmpValues[i] = getObjectId(myList.get(i));
                                // if myObj is an entity, we add it to children
                                if (Model.class.isInstance(myList.get(i))) {
                                    ymlObject.getChildren().add(getObjectId(myList.get(i)));
                                }
                            }
                            data.put(name, tmpValues);
                        }
                        valueIsSet = Boolean.TRUE;
                    }

                    // if field is a Map
                    if (Map.class.isInstance(field.get(jpaBase))) {
                        Logger.debug("Field  " + name + " type is Map");
                        Map myMap = (Map) field.get(jpaBase);
                        if (myMap != null && myMap.size() > 0) {
                            String[] tmpValues = new String[myMap.size()];
                            Iterator it = myMap.entrySet().iterator();
                            int i = 0;
                            while (it.hasNext()) {
                                Object myObj = it.next();
                                tmpValues[i] = getObjectId(myObj);
                                // if myObj is an entity, we add it to children
                                if (myObj != null && Model.class.isInstance(myObj)) {
                                    if (getObjectId(myObj) != null) {
                                        ymlObject.getChildren().add(getObjectId(myObj));
                                    }
                                }
                                i++;
                            }
                            data.put(name, tmpValues);
                        }
                        valueIsSet = Boolean.TRUE;
                    }

                    // if field is a Set
                    if (Set.class.isInstance(field.get(jpaBase))) {
                        Logger.debug("Field  " + name + " type is Set");
                        Set mySet = (Set) field.get(jpaBase);
                        if (mySet != null && mySet.size() > 0) {
                            String[] tmpValues = new String[mySet.size()];
                            Iterator it = mySet.iterator();
                            int i = 0;
                            while (it.hasNext()) {
                                Object myObj = it.next();
                                tmpValues[i] = getObjectId(myObj);
                                // if myObj is an entity, we add it to children
                                if (myObj != null && Model.class.isInstance(myObj)) {
                                    if (getObjectId(myObj) != null) {
                                        ymlObject.getChildren().add(getObjectId(myObj));
                                    }
                                }
                                i++;
                            }
                            data.put(name, tmpValues);
                        }
                        valueIsSet = Boolean.TRUE;
                    }

                    // if Lob annotation, then bigtext
                    if (field.isAnnotationPresent(Lob.class)) {
                        Logger.debug("Field  " + name + " type is a Lob");
                        if (field.get(jpaBase) != null) {
                            data.put(name, field.get(jpaBase).toString());
                        }
                        valueIsSet = Boolean.TRUE;
                    }

                    // if field is an object that extend Model
                    if (jpaBase != null && Model.class.isInstance(field.get(jpaBase))) {
                        Logger.debug("Field  " + name + " type is a Model");
                        ymlObject.getChildren().add(getObjectId(field.get(jpaBase)));
                        data.put(name, getObjectId(field.get(jpaBase)));
                        valueIsSet = Boolean.TRUE;
                    }

                    // if field is a date
                    if (Date.class.isInstance(field.get(jpaBase))) {
                        Logger.debug("Field  " + name + " type is Date");
                        SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd hh:mm:ss");
                        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                        Date myDate = (Date) sdf.parse(field.get(jpaBase).toString());
                        data.put(name, df.format(myDate));
                        valueIsSet = Boolean.TRUE;
                    }

                    // otherwise ...
                    if (!valueIsSet) {
                        Logger.debug("Field  " + name + " type is ??");
                        String tmpValue = "" + field.get(jpaBase);
                        data.put(name, tmpValue);
                        valueIsSet = Boolean.TRUE;
                    }

                    if (valueIsSet && !data.isEmpty()) {
                        // yml indentation
                        String value = yaml.dump(data).replaceAll("^", TAB);
                        // a little hack for scalar ... I have to find a
                        // better solution
                        value = value.replaceAll("- ", TAB + "- ");
                        // a little hack for tab String empty
                        stringObject += value;
                    }
                }
            }
        }
        ymlObject.setYmlValue(stringObject);

        return ymlObject;
    }

    /**
     * Method that return classname the object.
     * 
     * @param Object
     * @return real className
     */
    public static String getObjectClassName(Object object) {
        String classname = object.getClass().getSimpleName();
        if (classname.contains("_$$_")) {
            classname = classname.split("_")[0];
        }
        return classname;
    }

    /**
     * Method that return an id for the object.
     * 
     * @param Object
     * @return the id field value
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    public static String getObjectId(Object object) throws IllegalArgumentException, IllegalAccessException {
        JPABase jpaBase = (JPABase) object;
        String objectId = null;
        // if the object extend from the play's model class
        if (jpaBase != null && jpaBase instanceof Model) {
            // we take the model id
            Model myModel = ((Model) jpaBase);
            objectId = getObjectClassName(object);
            objectId += "_";
            objectId += myModel.getId();
        }
        // else we try to get value of the field with id annotation
        else {
            // we look up for the field with the id annotation
            Field fieldId = null;
            for (java.lang.reflect.Field field : jpaBase.getClass().getFields()) {
                if (field.getAnnotation(Id.class) != null) {
                    fieldId = field;
                }
            }
            if (fieldId != null) {
                objectId = fieldId.get(jpaBase).toString();
            }
        }

        // here we delete proxy annotation for classname (due to lazy, see
        // javassist)
        if (object.getClass().getCanonicalName().contains("_$$_javassist_")) {
            String[] elements = object.getClass().getCanonicalName().split("_");
            objectId = getObjectClassName(object) + "_" + elements[elements.length - 1];
        }

        return objectId;
    }

    /**
     * Method to get the DB dialect. Note: this method is a copy of play!
     * framework code (but it's private ...)
     * 
     * @param driver
     * @return String
     */
    public static String getDefaultDialect(String driver) {
        if (driver != null && driver.equals("org.hsqldb.jdbcDriver")) {
            return "org.hibernate.dialect.HSQLDialect";
        }
        else
            if (driver != null && driver.equals("com.mysql.jdbc.Driver")) {
                return "play.db.jpa.MySQLDialect";
            }
            else {
                String dialect = Play.configuration.getProperty("jpa.dialect");
                if (dialect != null) {
                    return dialect;
                }
                throw new UnsupportedOperationException("I do not know which hibernate dialect to use with " + driver
                        + ", use the property jpa.dialect in config file");
            }
    }

    /**
     * Method that return a Play EntytManager. Note: this method is a copy of
     * play! framework code.
     * 
     * @return EntityManager
     * @throws PropertyVetoException
     */
    public static EntityManager iniateJPA() throws PropertyVetoException {
        Properties p = Play.configuration;
        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setDriverClass(p.getProperty("db.driver"));
        ds.setJdbcUrl(p.getProperty("db.url"));
        ds.setUser(p.getProperty("db.user"));
        ds.setPassword(p.getProperty("db.pass"));
        ds.setAcquireRetryAttempts(1);
        ds.setAcquireRetryDelay(0);
        ds.setCheckoutTimeout(Integer.parseInt(p.getProperty("db.pool.timeout", "5000")));
        ds.setBreakAfterAcquireFailure(true);
        ds.setMaxPoolSize(Integer.parseInt(p.getProperty("db.pool.maxSize", "30")));
        ds.setMinPoolSize(Integer.parseInt(p.getProperty("db.pool.minSize", "1")));
        ds.setTestConnectionOnCheckout(true);

        List<Class> classes = Play.classloader.getAnnotatedClasses(Entity.class);
        Ejb3Configuration cfg = new Ejb3Configuration();
        cfg.setDataSource(ds);
        if (!Play.configuration.getProperty("jpa.ddl", "update").equals("none")) {
            cfg.setProperty("hibernate.hbm2ddl.auto", Play.configuration.getProperty("jpa.ddl", "update"));
        }
        cfg.setProperty("hibernate.dialect", getDefaultDialect(Play.configuration.getProperty("jpa.dialect")));
        cfg.setProperty("javax.persistence.transaction", "RESOURCE_LOCAL");
        if (Play.configuration.getProperty("jpa.debugSQL", "false").equals("true")) {
            org.apache.log4j.Logger.getLogger("org.hibernate.SQL").setLevel(Level.ALL);
        }
        else {
            org.apache.log4j.Logger.getLogger("org.hibernate.SQL").setLevel(Level.OFF);
        }
        // inject additional hibernate.* settings declared in Play!
        // configuration
        cfg.addProperties((Properties) Utils.Maps.filterMap(Play.configuration, "^hibernate\\..*"));

        try {
            Field field = cfg.getClass().getDeclaredField("overridenClassLoader");
            field.setAccessible(true);
            field.set(cfg, Play.classloader);
        } catch (Exception e) {
            Logger.error(e, "Error trying to override the hibernate classLoader (new hibernate version ???)");
        }
        for (Class<? extends Annotation> clazz : classes) {
            if (clazz.isAnnotationPresent(Entity.class)) {
                cfg.addAnnotatedClass(clazz);
                Logger.trace("JPA Model : %s", clazz);
            }
        }
        String[] moreEntities = Play.configuration.getProperty("jpa.entities", "").split(", ");
        for (String entity : moreEntities) {
            if (entity.trim().equals(""))
                continue;
            try {
                cfg.addAnnotatedClass(Play.classloader.loadClass(entity));
            } catch (Exception e) {
                Logger.warn("JPA -> Entity not found: %s", entity);
            }
        }
        Logger.trace("Initializing JPA ...");
        EntityManagerFactory entityManagerFactory = cfg.buildEntityManagerFactory();
        return entityManagerFactory.createEntityManager();
    }
}
