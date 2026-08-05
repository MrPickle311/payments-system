wizualizacja:
  - diagram stanów i inne htmle, spiąć to - done
  - wizualizacja danego joba w html - done
  - wizualizacja za pomocą grafany - done
  - wizualizacja partycjonowania w spring batch  - done
  - spiąć wszystko w Homepage - done 
  - wystaw actuatora z aplikacji - done
  - monitorowanie beanów springa  - done
  - wystawić openapi jako defaultowa strone dla kazdego serwisu
  - export metryk z maszyny stanów do grafany -brak
  - export metryk z jobów i partycjonowania do grafany - done
  - powiązanie danych z danym paymentem oraz kafką. Da się wszystkie dane jakoś ładnie wyświetlić  ? - done
  - na koniec przeygotować testplan, który pokaże mi jak partycjnowania działa pod obciążeniem. Partycjonowanie lokalne i zdalne. Mock regulatory service powinno odpowiadać bardzo wolno tak, by zaszła potrzeba partycjowania i zrównoleglenia pracy
  - tesplan również powinien pokazywać ladne przejscia miedzy stanami
  - pobawić się skalowaniem batch jobów

  - posprzątać kod:
    - wszystkie usługi powinny wystawić openapi - jeśli coś konsumuje inne, to trzeba wydzielić do shared module 
    - checkstyle ma wszysko czesać
    - 