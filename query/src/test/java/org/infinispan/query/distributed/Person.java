package org.infinispan.query.distributed;

import java.io.Serializable;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

@Indexed(index = "person")
public class Person implements Serializable {

    @Field(store = Store.NO, analyze=Analyze.NO)
    private String name;

    @Field(store = Store.NO, analyze=Analyze.NO)
    private String surname;

    public Person(String name, String surname) {
       this.name = name;
       this.surname = surname;
    }

 }