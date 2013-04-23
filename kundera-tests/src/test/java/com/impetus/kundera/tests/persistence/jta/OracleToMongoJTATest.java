/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.kundera.tests.persistence.jta;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Persistence;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.impetus.kundera.persistence.jta.KunderaJTAUserTransaction;

/**
 * @author vivek.mishra
 * 
 */
public class OracleToMongoJTATest
{
    private InitialContext initialContext;

    private UserTransaction userTransaction;

    private EntityManagerFactory emf;

    private EntityManager em;

    private Integer i=0;
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {      
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
        System.setProperty(Context.URL_PKG_PREFIXES, "org.apache.naming");

        initialContext = new InitialContext();
        initialContext.createSubcontext("java:comp");
        // This is what we need to bind to get handle of JTA's
        // UserTransaction.

        initialContext.bind("java:comp/UserTransaction", new KunderaJTAUserTransaction());

        emf = Persistence.createEntityManagerFactory("oracle_kvstore_JTA,addMongoJTA");
        em = emf.createEntityManager();
    }

    @Test
    public void testConcurrentPersist() throws NotSupportedException, SystemException, NamingException,
            SecurityException, IllegalStateException, RollbackException, HeuristicMixedException,
            HeuristicRollbackException
    {

        userTransaction = (UserTransaction) initialContext.lookup("java:comp/UserTransaction");

        userTransaction.begin();

        
        for (i = 0; i < 100; i++)
        {
           Runnable r =  onExecute();
           r.run();
        }
        
        userTransaction.commit();

        userTransaction.begin();
        // As data is commited, hence it should return values with other session.
        for (i = 0; i < 100; i++)
        {
        EntityManager em1 = emf.createEntityManager();
        Assert.assertNotNull(em1.find(OraclePersonnelOTOFKEntityJTA.class, "1_p" + i));
        }
    }

    private Runnable onExecute()
    {
        Runnable r = new Runnable()
        {


            @Override
            public void run()
            {
                OraclePersonnelOTOFKEntityJTA person = new OraclePersonnelOTOFKEntityJTA();
                person.setPersonId("1_p" + i);
                person.setPersonName("crossdata-store");
                HabitatOToOFKEntityJTA address = new HabitatOToOFKEntityJTA();
                address.setAddressId("1_a" + i);
                address.setStreet("my street");
                person.setAddress(address);
                try
                {
                    em.persist(person);
                }
                catch (Exception ex)
                {
                    HabitatOToOFKEntityJTA found = em.find(HabitatOToOFKEntityJTA.class, "1_a" + i);
                    Assert.assertNull(found);
                }
                
                // As data is not commited, hence it should return null with other session.
                EntityManager em1 = emf.createEntityManager();
                Assert.assertNull(em1.find(OraclePersonnelOTOFKEntityJTA.class, "1_p" + i));
            }
        };        
        return r;
    }

    
    
    
    @Test
    public void testPersist() throws NotSupportedException, SystemException, NamingException, SecurityException,
            IllegalStateException, RollbackException, HeuristicMixedException, HeuristicRollbackException
    {
        userTransaction = (UserTransaction) initialContext.lookup("java:comp/UserTransaction");
        em.setFlushMode(FlushModeType.COMMIT);

        userTransaction.begin();
        OraclePersonnelOTOFKEntityJTA person = new OraclePersonnelOTOFKEntityJTA();
        person.setPersonId("1_p");
        person.setPersonName("crossdata-store");
        HabitatOToOFKEntityJTA address = new HabitatOToOFKEntityJTA();
        address.setAddressId("1_a");
        address.setStreet("my street");
        person.setAddress(address);
        try
        {
            em.persist(person);
        }
        catch (Exception ex)
        {
            HabitatOToOFKEntityJTA found = em.find(HabitatOToOFKEntityJTA.class, "1_a");
            Assert.assertNull(found);
        }
        userTransaction.commit();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
        initialContext.unbind("java:comp/UserTransaction");
        initialContext.destroySubcontext("java:comp");
    }
}
