package edu.kse.easycaller;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.net.Uri;

import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM user")
    List<User> getAll();

    @Query("SELECT * FROM user WHERE uid IN (:userIdes)")
    List<User> getAllByIdes(int[] userIdes);

    @Query("SELECT * FROM user WHERE uid LIKE :id LIMIT 1")
    User getById(int id);

    @Query("SELECT * FROM user WHERE first_name LIKE :first AND  last_name LIKE :last LIMIT 1")
    User getByName(String first, String last);

    @Query("SELECT * FROM user WHERE phone_number LIKE :phoneNumber LIMIT 1")
    User getByPhoneNumber(String phoneNumber);

    @Insert
    void insert(User users);

    @Insert
    void insertAll(User... users);

    @Delete
    void delete(User user);

    @Query("SELECT Count(*) FROM user")
    int getCount();
}