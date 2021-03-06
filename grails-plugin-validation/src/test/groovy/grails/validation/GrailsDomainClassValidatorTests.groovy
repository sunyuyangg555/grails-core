package grails.validation

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.GrailsDomainClass
import junit.framework.TestCase
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import org.springframework.validation.BindException

class GrailsDomainClassValidatorTests extends TestCase {
    GrailsApplication ga
    @Override
    protected void setUp() throws Exception {
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
@grails.persistence.Entity
class Book {
    Long id
    Long version
    String title
    Author author
    static belongsTo = Author
    static constraints = {
       title(blank:false, size:1..255)
       author(nullable:false)
    }
}
@grails.persistence.Entity
class Author {
    Long id
    Long version
    String name
    Address address
    Set books = new HashSet()
    static hasMany = [books:Book]
    static constraints = {
        address(nullable:false)
        name(size:1..255, blank:false)
    }
}
@grails.persistence.Entity
class Address {
    Long id
    Long version
    Author author
    String location
    static constraints = {
       author(nullable:false)
       location(blank:false)
    }
}
@grails.persistence.Entity
class Publisher {
    Long id
    Long version
    String name
    Set authors = new HashSet()
    static hasMany = [authors:Author]
    static mapping = {
        authors cascade: 'save-update'
    }
}
        ''')

        ga = new DefaultGrailsApplication(gcl.loadedClasses)
        ga.initialise()
        new MappingContextBuilder(ga).build(gcl.loadedClasses)
    }


    void testCascadingValidation() {
        def bookClass = ga.getDomainClass("Book")
        def authorClass = ga.getDomainClass("Author")
        def addressClass = ga.getDomainClass("Address")

        def bookMetaClass = new ExpandoMetaClass(bookClass.clazz)
        def authorMetaClass = new ExpandoMetaClass(authorClass.clazz)
        def errorsProp = null
        def setter = { Object obj -> errorsProp = obj }

        bookMetaClass.setErrors = setter
        authorMetaClass.setErrors = setter
        bookMetaClass.initialize()
        authorMetaClass.initialize()

        def book = bookClass.newInstance()
        book.metaClass = bookMetaClass

        def bookValidator = ((GrailsDomainClass)bookClass).getValidator()
        def authorValidator = ((GrailsDomainClass)authorClass).getValidator()

        def errors = new BindException(book, book.class.name)

        bookValidator.validate(book, errors, true)

        assert errors.hasErrors()

        book.title = "Foo"
        def author = authorClass.newInstance()
        author.metaClass = authorMetaClass
        book.author = author

        errors = new BindException(book, book.class.name)
        bookValidator.validate(book, errors, true)

        // it should validate here because even though the author properties are not set, validation doesn't cascade
        // because a Book belongs to an Author and the same way persistence doesn't cascade so too validation doesn't

        assert !errors.hasErrors()

        book.author.name = "Bar"
        book.author.address = addressClass.newInstance()
        errors = new BindException(book, book.class.name)
        bookValidator.validate(book, errors, true)

        assert !errors.hasErrors()

        book.author.address.location = "UK"
        book.author.address.author = book.author
        errors = new BindException(book, book.class.name)
        bookValidator.validate(book, errors, true)

        assert !errors.hasErrors()

        def book2 = bookClass.newInstance()
        book2.metaClass = bookMetaClass

        author.books.add(book2)

        errors = new BindException(author, author.class.name)
        authorValidator.validate(author, errors, true)
        assert errors.hasErrors()

        book2.title = "Bar 2"
        book2.author = author

        errors = new BindException(author, author.class.name)
        authorValidator.validate(author, errors, true)
        assert !errors.hasErrors()

        def publisherClass = ga.getDomainClass("Publisher")

        def authorsProperty = publisherClass.getPersistentProperties().find { it.name == 'authors' }

        def publisherMetaClass = new ExpandoMetaClass(publisherClass.clazz)
        publisherMetaClass.setErrors = setter
        publisherMetaClass.initialize()

        // test cascading validation triggered by mapping DSL cascading save and updates.
        def publisher = publisherClass.newInstance()
        publisher.metaClass = publisherMetaClass
        publisher.name = "Book Publisher"

        def publisherValidator = ((GrailsDomainClass)publisherClass).getValidator()

        errors = new BindException(publisher, publisher.class.name)

        publisherValidator.validate(publisher, errors, true)
        assert !errors.hasErrors()

        def publisherAuthor = authorClass.newInstance()
        publisherAuthor.metaClass = authorMetaClass
        publisher.authors.add(publisherAuthor)

        errors = new BindException(publisher, publisher.class.name)

        publisherValidator.validate(publisher, errors, true)
        assert errors.hasErrors()

        publisherAuthor.name = "Foo"
        publisherAuthor.address = addressClass.newInstance()
        publisherAuthor.address.location = "Portugal"
        publisherAuthor.address.author = publisherAuthor

        errors = new BindException(publisher, publisher.class.name)

        publisherValidator.validate(publisher, errors, true)
        assert !errors.hasErrors()

    }

    @Override
    protected void tearDown() throws Exception {
        FieldEntityAccess.clearReflectors()
    }

}
