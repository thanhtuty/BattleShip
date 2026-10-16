/*
  Run only after 01_BattleShipDB.sql.
  Edit the sample password below and enable SQL Server Mixed Mode authentication.
  Execute in SSMS as a SQL Server administrator.
*/
USE master;
GO
IF SUSER_ID(N'battleship_app') IS NULL
    CREATE LOGIN battleship_app WITH PASSWORD=N'Change_This_Password_123!', CHECK_POLICY=ON;
ELSE
    ALTER LOGIN battleship_app WITH PASSWORD=N'Change_This_Password_123!', CHECK_POLICY=ON;
GO
USE BattleShipDB;
GO
IF USER_ID(N'battleship_app') IS NULL
    CREATE USER battleship_app FOR LOGIN battleship_app;
GO
ALTER ROLE db_datareader ADD MEMBER battleship_app;
ALTER ROLE db_datawriter ADD MEMBER battleship_app;
GO
