package com.budgetbee.service;

import com.budgetbee.model.Category;
import com.budgetbee.model.IncomeCategory;
import com.budgetbee.repo.CategoryRepository;
import com.budgetbee.repo.IncomeCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Gives every new account its own starter categories (with keywords), which the user can then edit. */
@Service
public class DefaultsService {

    private final CategoryRepository categories;
    private final IncomeCategoryRepository incomeCategories;

    public DefaultsService(CategoryRepository categories, IncomeCategoryRepository incomeCategories) {
        this.categories = categories;
        this.incomeCategories = incomeCategories;
    }

    public void seed(Long userId) {
        List<Category> cats = List.of(
            new Category("Snacks", "🍿", "snack,snacks,samosa,chips,biscuit,chocolate,tea,coffee,juice,bakery,puff"),
            new Category("Food", "🍛", "food,lunch,dinner,breakfast,meal,biryani,restaurant,swiggy,zomato,mess,canteen,pizza,burger"),
            new Category("Travel", "🚌", "travel,auto,cab,uber,ola,rapido,bus,metro,train,petrol,diesel,fuel,fare,ticket,parking,toll"),
            new Category("Groceries", "🛒", "grocery,groceries,vegetables,fruits,milk,provisions,supermarket,dmart,bigbasket,blinkit,zepto"),
            new Category("Bills", "💡", "bill,bills,recharge,electricity,wifi,broadband,rent,gas,water,dth,insurance,emi"),
            new Category("Shopping", "🛍️", "shopping,clothes,shirt,shoes,amazon,flipkart,myntra,dress,gift"),
            new Category("Health", "💊", "health,medicine,medical,pharmacy,doctor,hospital,clinic,gym"),
            new Category("Entertainment", "🎬", "movie,movies,netflix,spotify,game,games,concert,hotstar,prime"),
            new Category("Education", "📚", "book,books,course,fees,exam,udemy,stationery"),
            new Category("Others", "📦", ""));
        cats.forEach(c -> c.userId = userId);
        categories.saveAll(cats);

        List<IncomeCategory> inc = List.of(
            new IncomeCategory("Salary", "💼"),
            new IncomeCategory("Pocket money", "👛"),
            new IncomeCategory("Friends and family", "🤝"),
            new IncomeCategory("Refund", "↩️"),
            new IncomeCategory("Gift", "🎁"),
            new IncomeCategory("Interest", "🏦"),
            new IncomeCategory("Other", "📥"));
        inc.forEach(c -> c.userId = userId);
        incomeCategories.saveAll(inc);
    }
}
