package io.typish.uaar.disc;

class User {
    String email ;
    String circolo;
    int id;
    String username;

    @Override
    public String toString() {
        return "User{" + "email='" + email + '\'' + ", circolo='" + circolo + '\'' + ", id=" + id + ", username='" + username + '\'' + '}';
    }
}
