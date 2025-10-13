import java.util.Scanner;
import java.util.InputMismatchException;

public class Main {
    /**
     * <p>
     *     O método main é o método principal da aplicacào, sendo este o primeiro a ser executado.
     *     ====Adicionar o que vao fazer aqui====
     *     Servirá também para fazer display do menu para o utilizador poder escolher que operacào quer fazer, pedindo
     *     um input numérico, correspondente a uma operacão
     * </p>
     */
    public static void main(String[] args) {
        Scanner myObj = new Scanner(System.in);

        while(true){
            try{
                System.out.println(
                        "Digite o valor da operacào que deseja executar:\n" +
                        "1. Pesquisa\n" +
                        "-1. Sair"
                );
                int option = myObj.nextInt();
                switch (option){
                    case 1:
                        System.out.println("Opcào 1 escolhida");
                        break;
                    case -1:
                        System.out.println("A terminar programa...");
                        return;
                    default:
                        throw new UserInputExeption("Opcão inválida, tente novamente");
                }
                break; //LExit loop if there's no errors;

            } catch (InputMismatchException e) {
                System.out.println("Input deve ser um número, tente novamente");
                myObj.nextLine();
            }catch (UserInputExeption e){
                System.out.println(e.getMessage());
            }
        }

    }

    /**
     * <p>
     *     O método menu servirá para o utilizador poder escolher que operacào quer fazer, pedindo
     *     um input numérico, correspondente a uma operacào
     * </p>
     */
    private static void pesquisarTermos(){

    }

}