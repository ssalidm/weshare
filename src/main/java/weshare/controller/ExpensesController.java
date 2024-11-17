package weshare.controller;
import io.javalin.http.Handler;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.jetbrains.annotations.NotNull;
import weshare.model.*;
import weshare.persistence.ExpenseDAO;
import weshare.persistence.PersonDAO;
import weshare.server.Routes;
import weshare.server.ServiceRegistry;
import weshare.server.WeShareServer;
import javax.money.MonetaryAmount;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import static weshare.model.MoneyHelper.ZERO_RANDS;

public class ExpensesController {

    public static final Handler view = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<Expense> expenses = expensesDAO.findExpensesForPerson(personLoggedIn);
        ArrayList<Expense> expenseArrayList = new ArrayList<>();
        for (Expense expense : expenses) {
            if(!expense.isFullyPaidByOthers()){
                expenseArrayList.add(expense);
            }
        }
        Map<String, Object> viewModel = new HashMap<>(Map.of("expenses", expenseArrayList));
        MonetaryAmount grand = ZERO_RANDS;
        for (Expense expense : expenseArrayList) {
            grand = grand.add(expense.getAmount().subtract(expense.totalAmountForPaymentsReceived()));
        }

        viewModel.put("total", grand);
        context.render("expenses.html", viewModel);
    };

    public static final Handler newExpense = context -> {
        context.render("newexpense.html");
    };

    public static final Handler payment_request = context -> {

        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        UUID uuid = UUID.fromString(context.queryParam("expenseId"));
        Optional<Expense> expense = expensesDAO.get(uuid);
        Map<String, Object> viewModel = (Map.of("expense", expense.get()));
        context.render("paymentrequest.html", viewModel);
    };

    public static final Handler payment_request_sent = context -> {

        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person person = WeShareServer.getPersonLoggedIn(context);
        Collection<PaymentRequest> paymentRequests = expensesDAO.findPaymentRequestsSent(person);
        Map<String,Object> view = new HashMap<>(Map.of("newexpenses", paymentRequests));

        MonetaryAmount grand = ZERO_RANDS;
        for (PaymentRequest pr : paymentRequests) {
            grand = grand.add(pr.getAmountToPay());


        }


        view.put("total", grand);
        context.render("paymentrequests_sent.html", view);
    };



    public static final Handler payment_request_Action = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        UUID uuid = UUID.fromString(context.formParamAsClass("expense_id", String.class).get());
        Optional<Expense> expense = expensesDAO.get(uuid);
        Expense expense1 = expense.get();
        Person person = new Person(context.formParamAsClass("email", String.class).get());
        LocalDate date = LocalDate.parse(context.formParamAsClass("due_date", String.class)
                .check(Objects::nonNull, "date is required")
                .get(), DateHelper.DD_MM_YYYY);

        MonetaryAmount amount = MoneyHelper.amountOf(context.formParamAsClass("amount", Long.class)
                .check(Objects::nonNull, "amount is required")
                .get());
        expense1.requestPayment(person, amount, date);
        expensesDAO.save(expense1);
        context.redirect("/paymentrequest?expenseId=" + context.formParamAsClass("expense_id", String.class).get());
    };


    // This is posting the payment request received
    public static final Handler payment_request_Received_Action = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        UUID uuid = UUID.fromString(context.formParamAsClass("payment_request_id", String.class).get());
        Person person = WeShareServer.getPersonLoggedIn(context);

        for (PaymentRequest pr : expensesDAO.findPaymentRequestsReceived(person)) {
            if (pr.getId().equals(uuid)) {
                pr.pay(person, DateHelper.TODAY);
                Expense expense = new Expense(person, pr.getDescription(), pr.getAmountToPay(), DateHelper.TODAY);
                expensesDAO.save(expense);
                break;
            }
        }
        context.redirect(Routes.PAYMENT_REQUEST_received_sent);
    };


    public static final Handler payment_request_Received_sent = context -> {

        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person person = WeShareServer.getPersonLoggedIn(context);
        Collection<PaymentRequest> paymentRequests = expensesDAO.findPaymentRequestsReceived(person);
        Map view = new HashMap<>(Map.of("newexpenses", paymentRequests));
        MonetaryAmount grand = ZERO_RANDS;
        for (PaymentRequest pr : paymentRequests) {
            if (!pr.isPaid()) {
                grand = grand.add(pr.getAmountToPay());
            }

        }


        view.put("total", grand);
        context.render("paymentrequests_received.html", view);
    };


    public static final Handler expenseAction = context -> {

        ExpenseDAO expenseDAO = ServiceRegistry.lookup(ExpenseDAO.class);

        Person person = WeShareServer.getPersonLoggedIn(context);

        String description = context.formParamAsClass("description", String.class)
                .check(Objects::nonNull, "description is required")
                .get();

        LocalDate date = LocalDate.parse(context.formParamAsClass("date", String.class)
                .check(Objects::nonNull, "date is required")
                .get(), DateHelper.DD_MM_YYYY);

        MonetaryAmount amount = MoneyHelper.amountOf(context.formParamAsClass("amount", Long.class)
                .check(Objects::nonNull, "amount is required")
                .get());

        Expense expense1 = new Expense(person, description, amount, date);
        expenseDAO.save(expense1);
        context.sessionAttribute(WeShareServer.SESSION_USER_KEY, person);
        context.redirect(Routes.EXPENSES);
    };
}
