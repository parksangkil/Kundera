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
package com.impetus.kundera.metadata.model.attributes;

import java.lang.reflect.Field;

import javax.persistence.metamodel.Bindable.BindableType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.PluralAttribute.CollectionType;
import javax.persistence.metamodel.Type;

/**
 * TODO::::: comments required.
 * 
 * @author vivek.mishra
 * 
 * @param <E>
 */
public abstract class AbstractPluralAttribute<X,E,T> extends AbstractAttribute<X,E>
{
    
    private Class<T> collectionClazz;

    /**
     * @param attribType
     * @param attribName
     * @param persistenceAttribType
     * @param managedType
     * @param member
     */
    public AbstractPluralAttribute(Type<E> attribType, String attribName,
            javax.persistence.metamodel.Attribute.PersistentAttributeType persistenceAttribType,
            ManagedType<X> managedType, Field member, Class<T> clazz)
    {
        super(attribType, attribName, persistenceAttribType, managedType, member);
        this.collectionClazz = clazz;
    }

    /* (non-Javadoc)
     * @see com.impetus.kundera.metadata.model.attributes.AbstractAttribute#getBindableType()
     */
    @Override
    public javax.persistence.metamodel.Bindable.BindableType getBindableType()
    {
        return BindableType.PLURAL_ATTRIBUTE;
    }
    
    /* (non-Javadoc)
     * @see com.impetus.kundera.metadata.model.attributes.AbstractAttribute#isCollection()
     */
    @Override
    public boolean isCollection()
    {
        return true;
    }

    /**
     * Return the collection type.
     * 
     * @return collection type
     */
    public abstract CollectionType getCollectionType();

    /**
     * Returns get java type.
     * 
     * @return java type.
     */
    protected Class<T> getBoundJavaType()
    {
        return collectionClazz;
    }

}