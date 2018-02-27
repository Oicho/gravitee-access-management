/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserServiceImpl implements UserService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Single<Set<User>> findByDomain(String domain) {
        LOGGER.debug("Find users by domain: {}", domain);
        return userRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find users by domain", ex));
                });
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("Find users by domain: {}", domain);
        return userRepository.findByDomain(domain, page, size)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find users by domain", ex));
                });
    }

    @Override
    public Maybe<User> findById(String id) {
        LOGGER.debug("Find user by id : {}", id);
        return userRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", id), ex));
                });
    }


    @Override
    public Maybe<User> loadUserByUsernameAndDomain(String domain, String username) {
        LOGGER.debug("Find user by username and domain: {} {}", username, domain);
        return userRepository.findByUsernameAndDomain(username, domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID: {} for the domain {}", username, domain, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s for the domain %s", username, domain), ex));
                });
    }

    @Override
    public Single<User> create(String domain, NewUser newUser) {
        LOGGER.debug("Create a new user {} for domain {}", newUser, domain);

        String userId = UUID.toString(UUID.random());

        User user = new User();
        user.setId(userId);
        user.setDomain(domain);
        user.setUsername(newUser.getUsername());

        if (newUser.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(newUser.getPassword()));
        }

        user.setFirstName(newUser.getFirstName());
        user.setLastName(newUser.getLastName());
        user.setEmail(newUser.getEmail());
        user.setSource(newUser.getSource());
        user.setClient(newUser.getClient());
        user.setLoggedAt(newUser.getLoggedAt());
        user.setLoginsCount(newUser.getLoginsCount());
        user.setAdditionalInformation(newUser.getAdditionalInformation());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        return userRepository.create(user)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a user", ex));
                });
    }

    @Override
    public Single<User> update(String domain, String id, UpdateUser updateUser) {
        LOGGER.debug("Update a user {} for domain {}", id, domain);

        return userRepository.findById(id)
                .map(user -> Optional.of(user))
                .defaultIfEmpty(Optional.empty())
                .toSingle()
                .flatMap(userOpt -> {
                    if (!userOpt.isPresent()) {
                        throw new UserNotFoundException(id);
                    }

                    User oldUser = userOpt.get();

                    if (updateUser.getPassword() != null) {
                        oldUser.setPassword(passwordEncoder.encode(updateUser.getPassword()));
                    }

                    oldUser.setFirstName(updateUser.getFirstName());
                    oldUser.setLastName(updateUser.getLastName());
                    oldUser.setEmail(updateUser.getEmail());
                    oldUser.setSource(updateUser.getSource());
                    oldUser.setClient(updateUser.getClient());
                    oldUser.setLoggedAt(updateUser.getLoggedAt());
                    oldUser.setLoginsCount(updateUser.getLoginsCount());
                    oldUser.setUpdatedAt(new Date());
                    oldUser.setAdditionalInformation(updateUser.getAdditionalInformation());

                    return userRepository.update(oldUser);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                });
    }

    @Override
    public Single<Irrelevant> delete(String userId) {
        LOGGER.debug("Delete user {}", userId);

        return userRepository.findById(userId)
                .isEmpty()
                    .flatMap(empty -> {
                        if (empty) {
                            throw new UserNotFoundException(userId);
                        }

                        return userRepository.delete(userId);
                    })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete user: {}", userId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete user: %s", userId), ex));
                });
    }
}
