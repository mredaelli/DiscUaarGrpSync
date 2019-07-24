package io.typish.uaar.disc;

class User {
    String email ;
    String circolo;
    int id;
    String username;
    String nome;
    String citta;
    Integer age;

    @Override
    public String toString() {
        return "User{" +
                "email='" + email + '\'' +
                ", circolo='" + circolo + '\'' +
                ", id=" + id +
                ", username='" + username + '\'' +
                ", nome='" + nome + '\'' +
                ", citta='" + citta + '\'' +
                ", age=" + age +
                '}';
    }
}
