package com.sm.instagram.platform.user;

import com.sm.instagram.platform.common.repository.AbstractMultiBagFetchRepositoryImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository implementation for User that handles complex fetching
 * using multiple queries to avoid MultipleBagFetchException.
 */
@Repository
public class UserRepositoryImpl 
        extends AbstractMultiBagFetchRepositoryImpl<User, Long> 
        implements UserRepositoryCustom {
    
    @Override
    protected Class<User> getEntityClass() {
        return User.class;
    }
    
    @Override
    protected String getIdFieldName() {
        return "id";
    }
    
    @Override
    protected List<String> getFetchQueries() {
        return List.of(
            // Query 1: Fetch User with addresses (first bag)
            "SELECT DISTINCT u FROM User u " +
            "LEFT JOIN FETCH u.addresses " +
            "WHERE u.id IN :ids",
            
            // Query 2: Fetch User with socialConnections (second bag)
            "SELECT DISTINCT u FROM User u " +
            "LEFT JOIN FETCH u.socialConnections " +
            "WHERE u.id IN :ids"
        );
    }
}
