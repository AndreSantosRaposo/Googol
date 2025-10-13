public class UserInputExeption extends Exception {
    public UserInputExeption(String errMessage){

        super(errMessage + "tente novamente");
    }
}
